<template>
  <div id="agreementPage">
    <h2 class="page-title">{{ isPrivacy ? '隐私政策' : '用户协议' }}</h2>
    <a-tabs v-model:activeKey="activeTab" centered class="agreement-tabs">
      <a-tab-pane key="user" tab="用户协议">
        <div class="agreement-card">
          <p class="update-date">更新日期：2026 年 9 月 6 日</p>

          <h3>一、协议说明</h3>
          <p>欢迎使用视界云图库（以下简称"本平台"）。本协议是您与本平台之间关于使用本平台各项服务所订立的约定。您在注册、登录、使用本平台服务前，请务必仔细阅读并充分理解本协议全部内容，特别是免除或限制责任的条款。您勾选"我已阅读并同意用户协议和隐私政策"并完成注册，即视为您已阅读并同意本协议的全部内容。</p>

          <h3>二、账号注册与安全</h3>
          <p>1. 您承诺以真实、合法、有效的信息注册账号，不得冒用他人身份信息。</p>
          <p>2. 您应妥善保管账号及密码，并对该账号下的所有行为负责。因您保管不善造成的损失，由您自行承担。</p>
          <p>3. 账号仅限本人使用，不得出借、出租、转让或售卖。</p>

          <h3>三、用户行为规范</h3>
          <p>1. 您在使用本平台时，必须遵守《中华人民共和国网络安全法》《中华人民共和国著作权法》等法律法规。</p>
          <p>2. 您不得上传、发布、传播含有以下内容的图片或信息：反对宪法所确定的基本原则的；危害国家安全、泄露国家秘密的；煽动民族仇恨、破坏民族团结的；侵害他人名誉权、肖像权、知识产权等合法权益的；散布谣言、扰乱社会秩序的；法律、行政法规禁止的其他内容。</p>
          <p>3. 您应对上传的图片享有合法权利或已获得合法授权，因上传行为引发的全部法律责任由您承担。</p>

          <h3>四、AI 生成内容说明</h3>
          <p>1. 本平台提供的 AI 扩图等功能生成的图片属于人工智能生成内容（AIGC）。根据《人工智能生成合成内容标识办法》等相关规定，此类内容将添加"AI 生成"标识。</p>
          <p>2. AI 生成内容可能存在瑕疵或不准确之处，仅供您参考与个人使用，您不应将其用于违法违规用途。</p>

          <h3>五、知识产权</h3>
          <p>1. 本平台的logo、页面设计、程序代码等均受法律保护，未经许可不得擅自使用。</p>
          <p>2. 您上传图片的知识产权归属依照法律规定及您与相关权利方的约定确定。</p>

          <h3>六、服务的变更与终止</h3>
          <p>1. 本平台有权根据业务发展需要变更、中断或终止部分或全部服务。</p>
          <p>2. 您可随时停止使用本平台服务，也可依照平台规则申请注销账号。账号注销后，我们将依法删除或匿名化处理您的个人信息。</p>

          <h3>七、免责声明</h3>
          <p>1. 因不可抗力、网络故障、系统维护等原因导致服务中断或数据丢失的，本平台不承担赔偿责任，但将尽合理努力减少损失。</p>
          <p>2. 您因违反本协议或法律法规而引发的纠纷及后果，由您自行承担。</p>

          <h3>八、其他</h3>
          <p>本协议的订立、执行与解释均适用中华人民共和国法律。如本协议任何条款被认定无效，不影响其余条款的效力。本平台有权适时修订本协议，修订后的协议将在本页面公布，若您继续使用本平台服务，视为您接受修订后的协议。</p>
        </div>
      </a-tab-pane>
      <a-tab-pane key="privacy" tab="隐私政策">
        <div class="agreement-card">
          <p class="update-date">更新日期：2026 年 9 月 6 日</p>

          <h3>一、引言</h3>
          <p>视界云图库（以下简称"本平台"）深知个人信息对您的重要性，我们将按照《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》等法律法规的要求，以谨慎的态度处理您的个人信息。请您在使用服务前仔细阅读本政策。</p>

          <h3>二、我们收集的信息</h3>
          <p>1. 账号信息：注册时收集的邮箱地址，用于注册登录、接收验证码。</p>
          <p>2. 个人资料：您自愿提供的昵称、头像、个性签名。</p>
          <p>3. 图片内容：您上传的图片及对应的尺寸、格式、体积、主色调等技术信息。</p>
          <p>4. 设备与日志信息：为保障服务安全所必需的访问日志信息。</p>

          <h3>三、我们如何使用信息</h3>
          <p>1. 用于账号注册、登录验证、身份校验。</p>
          <p>2. 用于提供图片存储、展示、检索、AI 扩图等核心功能。</p>
          <p>3. 用于向您发送与账号安全、服务通知相关的邮件验证码。</p>
          <p>4. 用于预防、发现和处置欺诈、滥用等危害账号安全的行为。</p>

          <h3>四、信息的共享与披露</h3>
          <p>1. 除以下情形外，未经您同意我们不会与第三方共享您的个人信息：事先获得您明确授权；根据法律法规或有权机关的强制性要求；为维护本平台或公众的合法权益。</p>
          <p>2. 我们不会将您的个人信息用于任何与本平台服务无关的用途。</p>

          <h3>五、信息的存储与保护</h3>
          <p>1. 您的个人信息存储于中华人民共和国境内的服务器。</p>
          <p>2. 我们采取加密传输、访问控制、日志审计等安全技术措施，防止信息被未经授权地访问、篡改、泄露或丢失。</p>
          <p>3. 我们仅在为您提供服务所必需的期限内保留您的个人信息。</p>

          <h3>六、您的权利</h3>
          <p>1. 查询与更正：您可在用户中心查看并修改您的个人资料。</p>
          <p>2. 删除：您可删除自己上传的图片。</p>
          <p>3. 账号注销：您可在用户中心申请注销账号。账号注销后，我们将停止为您提供服务，并依法对您的个人信息进行删除或匿名化处理。</p>

          <h3>七、未成年人保护</h3>
          <p>本平台主要面向成年人提供服务。若您为未满 14 周岁的未成年人，请在监护人陪同下阅读本政策，并在取得监护人同意后使用本平台服务。</p>

          <h3>八、政策的更新</h3>
          <p>我们可能适时更新本政策。重大变更时，我们将在本页面显著位置公告。请您定期查阅本政策以了解最新内容。</p>

          <h3>九、联系我们</h3>
          <p>如您对本政策或个人信息保护有任何疑问、意见或建议，可通过平台内反馈渠道与我们联系，我们将在 15 个工作日内答复。</p>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const isPrivacy = computed(() => route.query.type === 'privacy')
const activeTab = ref<'user' | 'privacy'>(isPrivacy.value ? 'privacy' : 'user')

// 同步 tab 与 url，便于分享/回退
watch(activeTab, (tab) => {
  router.replace({ path: '/agreement', query: { type: tab === 'privacy' ? 'privacy' : 'user' } })
})
watch(isPrivacy, (v) => {
  activeTab.value = v ? 'privacy' : 'user'
})
</script>

<style scoped>
#agreementPage {
  max-width: 820px;
  margin: 0 auto;
}

.page-title {
  text-align: center;
  margin: 8px 0 24px;
  font-size: 26px;
}

.agreement-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
  padding: 28px 32px;
  line-height: 1.9;
  color: var(--text-primary-light);
  font-size: 14px;
}

.update-date {
  color: var(--text-disabled);
  font-size: 13px;
  text-align: right;
}

.agreement-card h3 {
  font-size: 15px;
  font-weight: 700;
  margin: 18px 0 8px;
  color: var(--text-primary-light);
}

.agreement-card p {
  margin: 0 0 10px;
  text-align: justify;
}

/* ===== 深色模式 ===== */
html.dark .agreement-card {
  background: rgba(45, 45, 45, 0.9);
  border-color: var(--border-color);
  color: rgba(240, 240, 240, 0.88);
}

html.dark .agreement-card h3 {
  color: var(--text-primary);
}

html.dark .update-date {
  color: rgba(179, 179, 179, 0.45);
}
</style>
