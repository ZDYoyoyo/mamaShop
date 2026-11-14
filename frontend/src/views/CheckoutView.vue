<script setup>
/**
 * 結帳頁（需要登入）：確認商品 → 選運送方式 → 填收件資訊 → 選付款方式 → 送出。
 *
 * - 金額只是「預估」給顧客看，實際金額由後端用最新的價格與運費計算（B08）
 * - 防重複下單有兩層：送出時停用按鈕（前端），以及進入頁面時取得的一次性識別碼（後端，R02）。
 *   同一個識別碼不管送幾次都只會建立一筆訂單（網路不穩重送、按鈕沒擋住的連點）
 * - 成功後移除已下單的品項：貨到付款導向訂單完成頁；
 *   線上付款（P01）接著呼叫「前往付款」，整頁跳到綠界。前往付款失敗就導向訂單頁，那裡可以再按「前往付款」
 * - 線上付款要後端有開（/payments/options），沒開或查詢失敗就只顯示貨到付款
 * - 超商取貨（P02）：後端有開電子地圖（/logistics/options）就用地圖選門市，否則手動填店號。
 *   按「選擇門市」→ 表單暫存到 sessionStorage → 整頁跳到綠界地圖 → 選好後回到 /checkout?cvsBrand=...&cvsStoreCode=...
 *   → 拿回暫存、填入門市、把網址參數清掉
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShippingMethods } from '@/api/catalog'
import { createOrder, getCheckoutToken } from '@/api/orders'
import { getCvsMapForm, getLogisticsOptions, redirectToCvsMap } from '@/api/logistics'
import { getPaymentOptions, redirectToPayment, startPayment } from '@/api/payments'
import LoadError from '@/components/LoadError.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'
import { saveCheckoutDraft, takeCheckoutDraft } from '@/utils/checkoutDraft'
import { CVS_BRANDS, cvsBrandLabel, PAYMENT_METHOD_LABELS } from '@/utils/labels'
import { CVS_NAME_MESSAGE, isValidCvsName, PHONE_MESSAGE, PHONE_PATTERN, toFieldErrorMap } from '@/utils/validation'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const cart = useCartStore()

const loading = ref(true)
const loadError = ref('')
const shippingMethods = ref([])
const notices = ref([])
/** 這次結帳的一次性識別碼（R02） */
const checkoutToken = ref('')

const formRef = ref()
const form = reactive({
  shippingMethod: '',
  // 預設帶入會員資料，送禮給別人時可以改
  recipientName: auth.user?.name ?? '',
  recipientPhone: auth.user?.phone ?? '',
  shippingAddress: '',
  cvsBrand: '',
  cvsStoreCode: '',
  cvsStoreName: '',
  /** 電子地圖帶回來的門市地址，只是顯示給顧客確認，不送到後端 */
  cvsStoreAddress: '',
  note: '',
  paymentMethod: 'COD',
})

/** 線上付款設定（P01）：{ onlinePaymentEnabled, paymentTimeoutMinutes } */
const paymentOptions = ref({ onlinePaymentEnabled: false, paymentTimeoutMinutes: 30 })
const isOnline = computed(() => form.paymentMethod === 'ONLINE')

/** 超商電子地圖（P02）：{ cvsMapEnabled } */
const logisticsOptions = ref({ cvsMapEnabled: false })
const mapError = ref('')
const mapLoading = ref(false)

const isHomeDelivery = computed(() => form.shippingMethod === 'HOME_DELIVERY')
const isCvsPickup = computed(() => form.shippingMethod === 'CVS_PICKUP')
const selectedMethod = computed(() => shippingMethods.value.find((m) => m.code === form.shippingMethod))
const shippingFee = computed(() => Number(selectedMethod.value?.fee ?? 0))
const estimatedTotal = computed(() => (Math.round(cart.subtotal * 100) + Math.round(shippingFee.value * 100)) / 100)

const rules = computed(() => ({
  shippingMethod: [{ required: true, message: '請選擇運送方式', trigger: 'change' }],
  recipientName: [
    { required: true, message: '請輸入收件人姓名', trigger: 'blur' },
    { max: 50, message: '最多 50 個字', trigger: 'blur' },
    // 超商取貨要核對證件，綠界限制姓名格式（P03）
    {
      validator: (_rule, value, callback) =>
        !isCvsPickup.value || !value || isValidCvsName(value) ? callback() : callback(new Error(CVS_NAME_MESSAGE)),
      trigger: 'blur',
    },
  ],
  recipientPhone: [
    { required: true, message: '請輸入收件人手機', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: PHONE_MESSAGE, trigger: 'blur' },
  ],
  // 地址、門市欄位用 v-if 依運送方式顯示，隱藏的欄位不會被驗證，
  // 所以可以直接標成必填（和後端的 @ValidShippingInfo 一致：宅配要地址、超商要門市）
  shippingAddress: [{ required: true, whitespace: true, message: '宅配需填寫收件地址', trigger: 'blur' }],
  cvsBrand: [{ required: true, message: '請選擇超商', trigger: 'change' }],
  cvsStoreCode: [{
    required: true,
    whitespace: true,
    message: logisticsOptions.value.cvsMapEnabled ? '請選擇取貨門市' : '請填寫門市店號',
    trigger: 'blur',
  }],
  cvsStoreName: [{ required: true, whitespace: true, message: '請填寫門市名稱', trigger: 'blur' }],
}))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // 同時更新購物車（價格、庫存可能變了）與取得運送方式
    const [cartNotices, methods, token, options, logistics] = await Promise.all([
      cart.refresh(),
      getShippingMethods(),
      getCheckoutToken(),
      // 查不到付款、物流設定不影響結帳：當作只有貨到付款、手動填門市
      getPaymentOptions().catch(() => null),
      getLogisticsOptions().catch(() => null),
    ])
    if (options) paymentOptions.value = options
    if (logistics) logisticsOptions.value = logistics
    checkoutToken.value = token.token
    notices.value = cartNotices
    shippingMethods.value = methods
    if (methods.length > 0 && !form.shippingMethod) form.shippingMethod = methods[0].code
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (cart.items.length === 0) {
    router.replace({ name: 'cart' })
    return
  }
  applyCvsMapResult()
  load()
})

// ===== 超商電子地圖（P02） =====

/** 從電子地圖回來：拿回暫存的表單、填入門市、清掉網址參數（重新整理才不會又套用一次） */
function applyCvsMapResult() {
  const query = route.query
  if (!query.cvsBrand && !query.cvsMapError) return
  const draft = takeCheckoutDraft()
  if (draft) Object.assign(form, draft)
  if (query.cvsMapError) {
    form.shippingMethod = 'CVS_PICKUP'
    mapError.value = '門市資料有誤，請重新選擇門市'
  } else if (CVS_BRANDS.some((b) => b.value === query.cvsBrand)) {
    form.shippingMethod = 'CVS_PICKUP'
    form.cvsBrand = query.cvsBrand
    form.cvsStoreCode = String(query.cvsStoreCode ?? '')
    form.cvsStoreName = String(query.cvsStoreName ?? '')
    form.cvsStoreAddress = String(query.cvsStoreAddress ?? '')
  }
  router.replace({ name: 'checkout' })
}

function clearStore() {
  form.cvsStoreCode = ''
  form.cvsStoreName = ''
  form.cvsStoreAddress = ''
}

// 換了超商品牌：之前選的門市是別家的，要重選（手動填寫時不清，讓顧客自己改）
watch(
  () => form.cvsBrand,
  (brand, previous) => {
    if (logisticsOptions.value.cvsMapEnabled && previous && brand !== previous) clearStore()
  },
)

// 從線上付款改成貨到付款：之前的門市是用「不用代收貨款」選的，可能不能取貨付款，要重選
watch(
  () => form.paymentMethod,
  (method, previous) => {
    if (logisticsOptions.value.cvsMapEnabled && previous === 'ONLINE' && method === 'COD' && form.cvsStoreCode) {
      clearStore()
      mapError.value = '改成貨到付款後，請重新選擇可以取貨付款的門市'
    }
  },
)

async function openCvsMap() {
  mapError.value = ''
  if (!(await formRef.value.validateField('cvsBrand').catch(() => false))) return
  mapLoading.value = true
  try {
    const mapForm = await getCvsMapForm({
      brand: form.cvsBrand,
      collection: form.paymentMethod !== 'ONLINE', // 貨到付款要選可以代收貨款的門市
      mobile: window.matchMedia?.('(max-width: 640px)').matches ?? false,
    })
    saveCheckoutDraft(form)
    redirectMessage.value = '正在前往超商電子地圖…'
    redirectToCvsMap(mapForm)
  } catch (e) {
    mapError.value = e.message
  } finally {
    mapLoading.value = false
  }
}

const submitting = ref(false)
/** 正在跳到綠界（付款頁或電子地圖）：頁面離開前顯示這個訊息，避免再按一次 */
const redirectMessage = ref('')
const submitError = ref('')
const serverErrors = ref({})

async function submit() {
  // 已經在送出中就不再送（雙重保險：按鈕本身也會停用）
  if (submitting.value || redirectMessage.value) return
  submitError.value = ''
  serverErrors.value = {}
  if (!(await formRef.value.validate().catch(() => false))) return

  submitting.value = true
  const orderedItems = cart.purchasableItems.map((item) => ({ productId: item.productId, quantity: item.quantity }))
  try {
    const order = await createOrder({
      items: orderedItems,
      shippingMethod: form.shippingMethod,
      recipientName: form.recipientName,
      recipientPhone: form.recipientPhone,
      // 只送目前運送方式需要的欄位
      shippingAddress: isHomeDelivery.value ? form.shippingAddress : null,
      cvsBrand: isCvsPickup.value ? form.cvsBrand : null,
      cvsStoreCode: isCvsPickup.value ? form.cvsStoreCode : null,
      cvsStoreName: isCvsPickup.value ? form.cvsStoreName : null,
      note: form.note || null,
      paymentMethod: form.paymentMethod,
    }, checkoutToken.value)
    cart.removeItems(orderedItems.map((item) => item.productId))
    if (order.paymentMethod === 'ONLINE') {
      await goToPayment(order.orderNo)
    } else {
      router.replace({ name: 'checkout-complete', params: { orderNo: order.orderNo } })
    }
  } catch (e) {
    if (['ORDER_TOKEN_INVALID', 'ORDER_TOKEN_REQUIRED'].includes(e.code)) {
      // 識別碼過期（結帳頁開太久）：換一個新的，請顧客再按一次。
      // 舊的識別碼從來沒有成功下單過，所以換新的再送不會重複
      submitError.value = e.message
      await refreshCheckoutToken()
    } else if (['INSUFFICIENT_STOCK', 'PRODUCT_UNAVAILABLE'].includes(e.code)) {
      // 下單的瞬間被別人買走了：更新購物車，讓顧客看到最新庫存
      submitError.value = `${e.message}。購物車已更新，請確認後再送出。`
      notices.value = await cart.refresh()
    } else if (e.fieldErrors?.length) {
      serverErrors.value = toFieldErrorMap(e)
      submitError.value = e.message
    } else {
      submitError.value = e.message
    }
  } finally {
    submitting.value = false
  }
}

/** 訂單已經成立了，接著前往綠界付款。失敗（例如網路斷了）就到訂單頁，可以從那裡再按「前往付款」 */
async function goToPayment(orderNo) {
  try {
    const form = await startPayment(orderNo)
    redirectMessage.value = '正在前往綠界付款頁…'
    redirectToPayment(form)
  } catch {
    router.replace({ name: 'order-detail', params: { orderNo } })
  }
}

async function refreshCheckoutToken() {
  try {
    checkoutToken.value = (await getCheckoutToken()).token
  } catch {
    // 換不到也沒關係：下次送出時後端會再提示
  }
}
</script>

<template>
  <h1>結帳</h1>

  <!-- 購物車已經清掉了，畫面不要閃一下「沒有可以結帳的商品」 -->
  <el-result v-if="redirectMessage" icon="info" :title="redirectMessage" sub-title="請不要關閉視窗" />
  <el-skeleton v-else-if="loading" animated :rows="6" />
  <LoadError v-else-if="loadError" :message="loadError" @retry="load" />

  <template v-else>
    <el-alert v-if="notices.length" type="warning" :closable="false" show-icon class="block">
      <template #title>
        <div v-for="notice in notices" :key="notice">{{ notice }}</div>
      </template>
    </el-alert>

    <section class="block card">
      <h2>商品</h2>
      <el-empty v-if="cart.purchasableItems.length === 0" description="沒有可以結帳的商品">
        <RouterLink :to="{ name: 'cart' }"><el-button>回購物車</el-button></RouterLink>
      </el-empty>
      <ul v-else class="lines">
        <li v-for="item in cart.purchasableItems" :key="item.productId">
          <span class="line-name">{{ item.name }} × {{ item.quantity }}</span>
          <span>{{ formatPrice((Math.round(item.price * 100) * item.quantity) / 100) }}</span>
        </li>
      </ul>
      <RouterLink :to="{ name: 'cart' }" class="edit-link">修改購物車</RouterLink>
    </section>

    <el-form
      v-if="cart.purchasableItems.length > 0"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <section class="block card">
        <h2>運送方式</h2>
        <el-form-item prop="shippingMethod">
          <el-radio-group v-model="form.shippingMethod" class="methods">
            <el-radio v-for="method in shippingMethods" :key="method.code" :value="method.code" border size="large">
              {{ method.name }}（運費 {{ formatPrice(method.fee) }}）
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="isHomeDelivery">
          <el-form-item label="收件地址" prop="shippingAddress" :error="serverErrors.shippingAddress">
            <el-input v-model="form.shippingAddress" type="textarea" :rows="2" maxlength="255" autocomplete="street-address" />
          </el-form-item>
        </template>

        <template v-if="isCvsPickup">
          <el-form-item label="超商" prop="cvsBrand" :error="serverErrors.cvsBrand">
            <el-select v-model="form.cvsBrand" placeholder="請選擇超商">
              <el-option v-for="brand in CVS_BRANDS" :key="brand.value" :label="brand.label" :value="brand.value" />
            </el-select>
          </el-form-item>
          <!-- 電子地圖選門市（P02） -->
          <el-form-item
            v-if="logisticsOptions.cvsMapEnabled"
            label="取貨門市"
            prop="cvsStoreCode"
            :error="serverErrors.cvsStoreCode"
          >
            <div class="store">
              <div v-if="form.cvsStoreCode" class="store-info">
                <strong>{{ cvsBrandLabel(form.cvsBrand) }} {{ form.cvsStoreName }}</strong>（店號 {{ form.cvsStoreCode }}）
                <div v-if="form.cvsStoreAddress" class="hint">{{ form.cvsStoreAddress }}</div>
              </div>
              <el-button :loading="mapLoading" class="map-button" @click="openCvsMap">
                {{ form.cvsStoreCode ? '重新選擇門市' : '選擇門市' }}
              </el-button>
              <p v-if="mapError" class="map-error">{{ mapError }}</p>
            </div>
          </el-form-item>
          <!-- 沒開電子地圖：手動填寫 -->
          <template v-else>
            <el-form-item label="門市店號" prop="cvsStoreCode" :error="serverErrors.cvsStoreCode">
              <el-input v-model="form.cvsStoreCode" maxlength="20" inputmode="numeric" />
            </el-form-item>
            <el-form-item label="門市名稱" prop="cvsStoreName" :error="serverErrors.cvsStoreName">
              <el-input v-model="form.cvsStoreName" maxlength="50" placeholder="例如：信義門市" />
            </el-form-item>
            <p class="hint">可在超商官網的門市查詢找到店號與門市名稱</p>
          </template>
        </template>
      </section>

      <section class="block card">
        <h2>收件人</h2>
        <el-form-item label="姓名" prop="recipientName" :error="serverErrors.recipientName">
          <el-input v-model="form.recipientName" maxlength="50" autocomplete="name" />
        </el-form-item>
        <el-form-item label="手機" prop="recipientPhone" :error="serverErrors.recipientPhone">
          <el-input v-model="form.recipientPhone" type="tel" maxlength="10" inputmode="numeric" autocomplete="tel" />
        </el-form-item>
        <el-form-item label="備註（選填）" prop="note" :error="serverErrors.note">
          <el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </section>

      <section class="block card">
        <h2>付款方式</h2>
        <el-radio-group v-if="paymentOptions.onlinePaymentEnabled" v-model="form.paymentMethod" class="methods">
          <el-radio value="COD" border size="large">{{ PAYMENT_METHOD_LABELS.COD }}：收到商品時付款</el-radio>
          <el-radio value="ONLINE" border size="large">{{ PAYMENT_METHOD_LABELS.ONLINE }}</el-radio>
        </el-radio-group>
        <p v-else class="payment">{{ PAYMENT_METHOD_LABELS.COD }}：收到商品時付款</p>
        <p v-if="isOnline" class="hint">
          下單後會前往綠界科技的付款頁輸入信用卡（卡號不會經過本網站）。
          請在 {{ paymentOptions.paymentTimeoutMinutes }} 分鐘內完成付款，逾時訂單會自動取消。
        </p>
      </section>

      <section class="block card summary">
        <div class="row"><span>商品小計</span><span>{{ formatPrice(cart.subtotal) }}</span></div>
        <div class="row"><span>運費</span><span>{{ formatPrice(shippingFee) }}</span></div>
        <div class="row total"><span>總計</span><strong>{{ formatPrice(estimatedTotal) }}</strong></div>
        <p class="hint">實際金額以訂單成立時為準</p>

        <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon class="submit-error" />
        <el-button type="primary" size="large" native-type="submit" :loading="submitting" :disabled="submitting" class="submit">
          {{ submitting ? '送出中…' : isOnline ? '確認下單並前往付款' : '確認下單' }}
        </el-button>
      </section>
    </el-form>
  </template>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
}

h2 {
  font-size: 1.05rem;
  margin: 0 0 12px;
}

.block {
  margin-bottom: 16px;
}

.card {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.lines {
  list-style: none;
  padding: 0;
  margin: 0 0 8px;
}

.lines li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
}

.edit-link {
  display: inline-block;
  padding: 8px 0;
  font-size: 0.9rem;
}

.methods {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
}

.methods :deep(.el-radio) {
  margin-right: 0;
}

.hint {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 0.85rem;
}

.payment {
  margin: 0;
}

.summary {
  background: var(--color-surface);
}

.row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
}

.row.total {
  margin-top: 8px;
  font-size: 1.15rem;
}

.row.total strong {
  color: var(--color-primary);
}

.submit-error {
  margin-top: 12px;
}

.submit {
  width: 100%;
  margin-top: 12px;
}
.store {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}

.map-error {
  width: 100%;
  margin: 0;
  color: var(--el-color-danger);
  font-size: 0.9rem;
}

.store-info {
  flex: 1;
  min-width: 200px;
  line-height: 1.5;
}
</style>
