<template>
  <div class="page-container">
    <HeaderBar title="旅行规划" :show-back="true" />

    <div class="plan-header">
      <div class="plan-title">{{ planData.place }}旅行规划</div>
      <div class="plan-subtitle">AI智能定制 · {{ planData.date }}</div>
    </div>

    <div class="content-container">
      <div class="info-card">
        <div class="info-item">
          <span class="info-label">目的地：</span>
          <span>{{ planData.place }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">行程天数：</span>
          <span>{{ planData.days }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">出行人数：</span>
          <span>{{ planData.people }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">旅行预算：</span>
          <span>¥{{ planData.budget }}</span>
        </div>
      </div>

      <div v-if="loading" class="loading-container">
        <el-progress type="circle" :percentage="loadingPercentage" :status="loadingStatus"></el-progress>
        <p class="loading-text">{{ loadingText }}</p>
      </div>

      <div v-else class="plan-content" v-html="planData.content"></div>

      <div class="footer">
        <p>由AI智能助手生成 - {{ planData.date }}</p>
      </div>
    </div>

    <div class="action-bar">
      <el-button type="primary" class="action-button" @click="downloadPlan" :disabled="loading">
        <el-icon style="margin-right: 4px;"><Download /></el-icon> 下载规划
      </el-button>
      <el-button type="success" class="action-button" @click="sharePlan" :disabled="loading">
        <el-icon style="margin-right: 4px;"><Share /></el-icon> 分享
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'

const router = useRouter()

// AI 接口配置（原页面直连第三方大模型，非业务后端，不走 request）
const API_KEY = 'sk-4bf1b3faf02d404a9d7253cbc6cb4e0a'
const API_URL = 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions'

const planData = ref({
  content: '',
  place: '',
  days: '',
  people: '',
  budget: '',
  date: ''
})
const loading = ref(true)
const loadingPercentage = ref(0)
const loadingStatus = ref('')
const loadingText = ref('正在为您生成旅行规划...')
let loadingInterval = null

function loadPlanData() {
  const storedPlan = localStorage.getItem('travelPlan')
  if (storedPlan) {
    planData.value = JSON.parse(storedPlan)
    loading.value = planData.value.loading !== false

    if (loading.value) {
      startLoadingAnimation()
      generateAIPlan()
    }
  } else {
    ElMessage.error('未找到旅行规划数据')
    setTimeout(() => {
      router.back()
    }, 1500)
  }
}

function startLoadingAnimation() {
  loadingPercentage.value = 0
  loadingInterval = setInterval(() => {
    if (loadingPercentage.value < 90) {
      loadingPercentage.value += Math.floor(Math.random() * 10)
      if (loadingPercentage.value > 90) {
        loadingPercentage.value = 90
      }
      if (loadingPercentage.value < 30) {
        loadingText.value = '正在分析目的地信息...'
      } else if (loadingPercentage.value < 60) {
        loadingText.value = '正在规划最佳行程路线...'
      } else {
        loadingText.value = '正在优化预算分配...'
      }
    }
  }, 800)
}

function generateAIPlan() {
  const prompt = `请为我规划一个${planData.value.place}的${planData.value.days}天旅游攻略。
    游客人数：${planData.value.people}人
    预算：${planData.value.budget}元

    请提供非常详细的行程安排，包括：
    1. 每天的具体行程安排，包括景点游览顺序、停留时间
    2. 每天的用餐建议，包括当地特色美食推荐
    3. 住宿建议
    4. 交通方式建议
    5. 详细的预算分配
    6. 旅行小贴士（如穿着建议、最佳游览时间等）

    重要：请直接使用HTML标签格式化你的回答，使内容美观易读。

    1. 使用以下HTML结构和类名：
       - 每天行程：<div class="day-plan">
       - 预算部分：<div class="budget-plan">
       - 餐饮建议：<div class="meal-suggestion">
       - 景点介绍：<div class="attraction-item">
       - 旅行贴士：<div class="travel-tip">

    2. 使用表情符号增强可读性，例如：
       - 🏞️ 表示景点
       - 🍽️ 表示餐饮
       - 🏨 表示住宿
       - 🚗 表示交通
       - 💰 表示费用
       - ⏰ 表示时间
       - 📝 表示贴士
       - 🌟 表示推荐

    3. 使用适当的颜色和样式，但不要使用外部CSS，所有样式都内联在HTML标签中。

    4. 为每个景点提供简短但有价值的描述，包括历史背景或特色。

    5. 为每天的行程提供一个合理的时间表。`

  const messages = [
    {
      role: 'system',
      content: '你是一个专业的旅游规划师，擅长根据用户需求制定详细的旅游攻略，并使用HTML标签进行美观的排版。你的规划非常具体，包含时间安排、景点介绍、美食推荐、预算分配等详细信息，并使用表情符号使内容更加生动。'
    },
    {
      role: 'user',
      content: prompt
    }
  ]

  fetch(API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${API_KEY}`
    },
    body: JSON.stringify({
      model: 'qwen-plus',
      messages: messages,
      temperature: 0.7,
      max_tokens: 2500
    })
  })
    .then(response => response.json())
    .then(data => {
      if (data.choices && data.choices[0] && data.choices[0].message) {
        clearInterval(loadingInterval)
        loadingPercentage.value = 100
        loadingStatus.value = 'success'
        loadingText.value = '规划生成完成！'

        setTimeout(() => {
          const aiResponse = data.choices[0].message.content
          planData.value.content = aiResponse
          planData.value.loading = false
          localStorage.setItem('travelPlan', JSON.stringify(planData.value))
          loading.value = false
        }, 800)
      } else {
        handleApiError('API返回数据格式错误')
      }
    })
    .catch(error => {
      console.error('API调用失败:', error)
      handleApiError('网络请求失败')
    })
}

function handleApiError(message) {
  clearInterval(loadingInterval)
  loadingStatus.value = 'exception'
  loadingText.value = '生成规划失败，请返回重试'
  ElMessage.error(message || '生成规划失败，请稍后重试')
  setTimeout(() => {
    router.back()
  }, 5000)
}

function downloadPlan() {
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '准备下载中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  const fileName = `${planData.value.place}${planData.value.days}天旅行规划.html`

  const htmlContent = `
  <!DOCTYPE html>
  <html lang="zh-CN">
  <head>
    <meta charset="utf-8">
    <title>您的专属旅行规划</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
      body { font-family: 'Microsoft YaHei', Arial, sans-serif; margin: 0; padding: 20px; line-height: 1.6; color: #333; background-color: #f9f9f9; }
      .container { max-width: 800px; margin: 0 auto; background-color: #fff; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
      h1 { text-align: center; color: #2c3e50; margin-bottom: 30px; border-bottom: 2px solid #eee; padding-bottom: 15px; }
      h2 { color: #3498db; margin-top: 25px; border-left: 4px solid #3498db; padding-left: 10px; font-size: 20px; }
      h3 { color: #2980b9; margin-top: 20px; font-size: 18px; }
      .info-card { background: #f5f7fa; padding: 15px; border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #3498db; }
      .day-plan { margin-bottom: 30px; padding: 15px; border-radius: 8px; background-color: #f8f9fa; border-left: 4px solid #3498db; }
      .budget-plan { background-color: #ebf5fb; padding: 15px; border-radius: 8px; margin-top: 30px; border-left: 4px solid #27ae60; }
      .footer { text-align: center; margin-top: 40px; color: #7f8c8d; font-size: 14px; }
    </style>
  </head>
  <body>
    <div class="container">
      <h1>${planData.value.place}旅行规划</h1>
      <div class="info-card">
        <p><strong>目的地：</strong>${planData.value.place}</p>
        <p><strong>行程天数：</strong>${planData.value.days}天</p>
        <p><strong>出行人数：</strong>${planData.value.people}人</p>
        <p><strong>旅行预算：</strong>¥${planData.value.budget}</p>
        <p><strong>生成日期：</strong>${planData.value.date}</p>
      </div>
      ${planData.value.content}
      <div class="footer">
        <p>由AI智能助手生成 - ${planData.value.date}</p>
      </div>
    </div>
  </body>
  </html>`

  const blob = new Blob([htmlContent], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)

  try {
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    setTimeout(() => {
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
      loadingInstance.close()
      ElMessage({
        message: '旅行规划已下载',
        type: 'success',
        duration: 3000
      })
    }, 100)
  } catch (error) {
    console.error('下载失败:', error)
    loadingInstance.close()
    fallbackDownload(htmlContent, fileName, url)
  }
}

function fallbackDownload(htmlContent, fileName, url) {
  const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)

  if (isMobile) {
    const dataUri = 'data:text/html;charset=utf-8,' + encodeURIComponent(htmlContent)
    const newWindow = window.open(dataUri, '_blank')
    if (newWindow) {
      ElMessage({
        message: '请使用浏览器的"保存"功能保存页面',
        type: 'success',
        duration: 5000
      })
    } else {
      ElMessageBox.alert(
        `<div><p>下载遇到问题，请<a href="${url}" download="${fileName}" style="color:#409EFF;text-decoration:underline;">点击此处</a>直接下载</p></div>`,
        '下载提示',
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '关闭'
        }
      )
    }
  } else {
    ElMessageBox.alert(
      `<div><p>下载遇到问题，请<a href="${url}" download="${fileName}" style="color:#409EFF;text-decoration:underline;">点击此处</a>直接下载</p></div>`,
      '下载提示',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '关闭'
      }
    )
  }
}

function sharePlan() {
  if (navigator.share) {
    navigator.share({
      title: `${planData.value.place}${planData.value.days}天旅行规划`,
      text: `我为您定制了一份${planData.value.place}${planData.value.days}天的旅行规划，包含详细行程和预算安排，快来看看吧！`,
      url: window.location.href
    })
      .then(() => ElMessage.success('分享成功'))
      .catch((error) => {
        console.error('分享失败:', error)
        fallbackShare()
      })
  } else {
    fallbackShare()
  }
}

function fallbackShare() {
  ElMessageBox.alert(
    '<div style="text-align:center;">' +
    '<h3 style="margin-bottom:20px;">分享到</h3>' +
    '<div style="display:flex;justify-content:space-around;margin-bottom:10px;">' +
    '<div class="share-wechat-item" style="cursor:pointer;text-align:center;padding:10px;">' +
    '<div style="width:50px;height:50px;border-radius:10px;margin:0 auto 5px;background:#07C160;color:#fff;display:flex;align-items:center;justify-content:center;">微信</div>' +
    '<div>微信</div>' +
    '</div>' +
    '<div class="share-qq-item" style="cursor:pointer;text-align:center;padding:10px;">' +
    '<div style="width:50px;height:50px;border-radius:10px;margin:0 auto 5px;background:#12B7F5;color:#fff;display:flex;align-items:center;justify-content:center;">QQ</div>' +
    '<div>QQ</div>' +
    '</div>' +
    '</div>' +
    '</div>',
    '分享旅行规划',
    {
      dangerouslyUseHTMLString: true,
      showConfirmButton: false,
      showCancelButton: true,
      cancelButtonText: '取消',
      customClass: 'share-dialog'
    }
  )

  // v-html 内容不编译 Vue 事件，手动绑定（与原页面行为一致）
  setTimeout(() => {
    const wechatBtn = document.querySelector('.share-dialog .share-wechat-item')
    const qqBtn = document.querySelector('.share-dialog .share-qq-item')
    if (wechatBtn) {
      wechatBtn.addEventListener('click', () => {
        shareToWeChat()
        const close = document.querySelector('.share-dialog .el-message-box__close')
        if (close) close.click()
      })
    }
    if (qqBtn) {
      qqBtn.addEventListener('click', () => {
        shareToQQ()
        const close = document.querySelector('.share-dialog .el-message-box__close')
        if (close) close.click()
      })
    }
  }, 100)
}

function shareToWeChat() {
  const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)

  if (isMobile) {
    if (typeof WeixinJSBridge !== 'undefined') {
      ElMessage.info('请点击右上角"..."按钮，选择"分享给朋友"')
    } else {
      const a = document.createElement('a')
      a.href = 'weixin://'
      a.click()
      setTimeout(() => {
        ElMessageBox.alert('请手动分享当前页面到微信', '分享提示', {
          confirmButtonText: '我知道了'
        })
      }, 1500)
    }
  } else {
    const currentUrl = encodeURIComponent(window.location.href)
    const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${currentUrl}`
    ElMessageBox.alert(
      `<div style="text-align:center;">
        <p>请使用微信扫描下方二维码分享</p>
        <img src="${qrCodeUrl}" style="width:200px;height:200px;margin:10px 0;">
      </div>`,
      '微信分享',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '关闭'
      }
    )
  }
}

function shareToQQ() {
  const shareTitle = encodeURIComponent(`${planData.value.place}${planData.value.days}天旅行规划`)
  const shareSummary = encodeURIComponent(`我为您定制了一份${planData.value.place}${planData.value.days}天的旅行规划，包含详细行程和预算安排，快来看看吧！`)
  const shareUrl = encodeURIComponent(window.location.href)
  const shareImageUrl = encodeURIComponent('https://example.com/travel-logo.png')
  const qqShareUrl = `https://connect.qq.com/widget/shareqq/index.html?url=${shareUrl}&title=${shareTitle}&summary=${shareSummary}&pics=${shareImageUrl}`
  window.open(qqShareUrl, '_blank')
}

loadPlanData()

onBeforeUnmount(() => {
  if (loadingInterval) clearInterval(loadingInterval)
})
</script>

<style scoped>
.page-container {
  position: relative;
  min-height: 100vh;
  max-width: 800px;
  margin: 0 auto;
  background-color: #fff;
  box-shadow: 0 2px 10px rgba(0,0,0,0.1);
}
.content-container {
  padding: 20px;
}
.plan-header {
  background-color: #3498db;
  color: white;
  padding: 20px;
  text-align: center;
  margin-bottom: 20px;
  border-radius: 0 0 10px 10px;
}
.plan-title {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 10px;
}
.plan-subtitle {
  font-size: 14px;
  opacity: 0.9;
}
.info-card {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 5px;
  margin-bottom: 20px;
  border-left: 4px solid #3498db;
}
.info-item {
  margin-bottom: 8px;
}
.info-label {
  font-weight: bold;
  margin-right: 5px;
}
.plan-content {
  line-height: 1.6;
}
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: white;
  padding: 10px 20px;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.1);
  display: flex;
  justify-content: space-around;
  z-index: 100;
}
.action-button {
  flex: 1;
  margin: 0 5px;
}
.plan-content h2 {
  color: #3498db;
  margin-top: 25px;
  border-left: 4px solid #3498db;
  padding-left: 10px;
  font-size: 20px;
}
.plan-content h3 {
  color: #2980b9;
  margin-top: 20px;
  font-size: 18px;
}
.plan-content ul {
  margin-bottom: 20px;
  list-style-type: none;
  padding-left: 10px;
}
.plan-content li {
  margin-bottom: 10px;
  position: relative;
  padding-left: 25px;
}
.plan-content li::before {
  content: "✅";
  position: absolute;
  left: 0;
}
.day-plan {
  margin-bottom: 30px;
  padding: 15px;
  border-radius: 8px;
  background-color: #f8f9fa;
  border-left: 4px solid #3498db;
}
.budget-plan {
  background-color: #ebf5fb;
  padding: 15px;
  border-radius: 8px;
  margin-top: 30px;
  border-left: 4px solid #27ae60;
}
.meal-suggestion {
  background-color: #fef9e7;
  padding: 10px 15px;
  border-radius: 6px;
  margin: 10px 0;
  border-left: 3px solid #f39c12;
}
.attraction-item {
  background-color: #eaf2f8;
  padding: 10px 15px;
  border-radius: 6px;
  margin: 10px 0;
}
.travel-tip {
  background-color: #e8f8f5;
  padding: 10px 15px;
  border-radius: 6px;
  margin: 15px 0;
  border-left: 3px solid #1abc9c;
}
.footer {
  text-align: center;
  margin: 40px 0 80px;
  color: #7f8c8d;
  font-size: 14px;
}
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 50px 0;
  text-align: center;
}
.loading-text {
  margin-top: 20px;
  font-size: 16px;
  color: #606266;
}
</style>
