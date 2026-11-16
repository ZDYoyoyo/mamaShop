<script setup>
/**
 * 隱私權政策（D08 上線檢查的項目）。
 *
 * 內容是照「網站實際上蒐集、使用了哪些資料」寫的：之後功能有變（例如新增付款方式、換寄信服務、
 * 加上追蹤或廣告工具），這一頁要跟著改。商店的名稱與聯絡方式在 config/shop.js。
 *
 * ⚠️ 這不是法律意見：正式上線前請對照個人資料保護法確認，必要時請教專業人士（PLAN 也有提醒）。
 */
import { SHOP } from '@/config/shop'
</script>

<template>
  <article class="policy">
    <h1>隱私權政策</h1>
    <p class="muted">最後更新：{{ SHOP.privacyUpdatedAt }}</p>

    <el-alert
      v-if="SHOP.placeholder"
      type="warning"
      :closable="false"
      show-icon
      title="本頁為暫定版本，商店資料與聯絡方式尚未更新。"
      class="draft"
    />

    <p>
      {{ SHOP.name }}（以下稱「本店」，由 {{ SHOP.operator }} 經營）重視您的隱私。
      這份政策說明您使用本網站時，我們蒐集哪些資料、用來做什麼、會提供給誰，以及您有哪些權利。
    </p>

    <h2>一、我們蒐集哪些資料</h2>
    <ul>
      <li><strong>註冊會員時</strong>：姓名、Email、密碼、手機號碼（選填）。</li>
      <li><strong>下單時</strong>：收件人姓名、收件人手機、宅配地址或超商取貨門市、訂單備註、購買的商品與金額。</li>
      <li><strong>線上付款時</strong>：付款在綠界科技的頁面上進行，<strong>信用卡號不會經過、也不會儲存在本網站</strong>。我們只會收到付款結果（成功或失敗、交易編號、金額）。</li>
      <li><strong>使用網站時</strong>：為了維持網站運作與安全，伺服器會記錄連線的 IP 位址、時間與瀏覽的網址；登入失敗的次數會暫時記錄，用來防止他人猜測密碼。</li>
    </ul>

    <h2>二、資料用來做什麼</h2>
    <ul>
      <li>處理訂單：確認訂單、備貨、出貨、通知您訂單進度。</li>
      <li>必要時與您聯絡，例如確認訂單內容、通知取貨或處理退貨退款。</li>
      <li>會員登入與帳號安全。</li>
      <li>依法令保存交易紀錄。</li>
    </ul>
    <p>我們<strong>不會</strong>把您的資料用於上述以外的用途，也不會出售或提供給他人做行銷。</p>

    <h2>三、資料會提供給誰</h2>
    <p>為了完成訂單，以下資料會提供給協助我們的業者，他們只能用來完成各自的服務：</p>
    <ul>
      <li><strong>物流</strong>（宅配業者、超商及綠界科技的物流服務）：收件人姓名、手機、地址或取貨門市。</li>
      <li><strong>金流</strong>（綠界科技）：訂單編號、金額、商品名稱，用來建立付款。</li>
      <li><strong>寄信服務</strong>：您的 Email 與訂單通知信的內容。</li>
      <li><strong>主機服務</strong>：網站與資料庫放在雲端主機上。</li>
    </ul>
    <p>除此之外，只有在法律要求、或司法及主管機關依法要求時，才會提供您的資料。</p>

    <h2>四、資料怎麼保護</h2>
    <ul>
      <li>密碼經過不可還原的雜湊處理後才儲存，我們也看不到您的密碼。</li>
      <li>網站使用加密連線（HTTPS）傳輸資料。</li>
      <li>只有本店負責處理訂單的人員可以看到訂單與收件資料；其他會員看不到您的訂單。</li>
      <li>資料庫不對外開放，並定期備份。</li>
    </ul>

    <h2>五、瀏覽器裡儲存的資料</h2>
    <p>
      本網站不使用廣告或追蹤用的 Cookie，也沒有安裝第三方的分析或廣告工具。
      為了讓網站正常運作，您的瀏覽器會儲存：登入狀態（登出或過期後失效）、購物車內容、結帳時尚未送出的表單。
      這些資料只存在您的裝置上，清除瀏覽器的網站資料即可刪除。
    </p>

    <h2>六、資料保存多久</h2>
    <p>
      會員資料保存到您要求刪除帳號為止。訂單與交易紀錄會依稅務等法令要求的期間保存，期滿後刪除或去識別化。
    </p>

    <h2>七、您的權利</h2>
    <p>依個人資料保護法，您可以向我們：</p>
    <ul>
      <li>查詢或請求閱覽您的個人資料、請求提供複本；</li>
      <li>請求補充或更正；</li>
      <li>請求停止蒐集、處理或利用；</li>
      <li>請求刪除（依法令必須保存的交易紀錄除外）。</li>
    </ul>
    <p>
      請來信 <a :href="`mailto:${SHOP.contactEmail}`">{{ SHOP.contactEmail }}</a>，我們會在確認身分後盡快處理。
      若您不提供訂購所需的資料，我們將無法為您處理訂單。
    </p>

    <h2>八、政策的修改</h2>
    <p>這份政策有修改時，會更新本頁最上方的日期。重大變更會在網站上公告。</p>

    <h2>九、聯絡我們</h2>
    <p>
      對這份政策或您的個人資料有任何問題，請聯絡：{{ SHOP.operator }}，
      <a :href="`mailto:${SHOP.contactEmail}`">{{ SHOP.contactEmail }}</a>
    </p>
  </article>
</template>

<style scoped>
.policy {
  max-width: 720px;
  margin: 0 auto;
  line-height: 1.8;
}

h1 {
  font-size: 1.5rem;
  margin-bottom: 4px;
}

h2 {
  font-size: 1.1rem;
  margin: 28px 0 8px;
}

ul {
  padding-left: 1.4em;
}

li {
  margin: 4px 0;
}

.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
  margin-top: 0;
}

.draft {
  margin: 12px 0;
}
</style>
