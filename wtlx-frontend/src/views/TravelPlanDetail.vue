<template>
  <div class="detail-page">
    <HeaderBar title="我的旅行计划" :show-back="true" />

    <div class="detail-container" v-if="planDetail">
      <div class="date-info">
        <div class="date-range">{{ planDetail.startDate }} — {{ planDetail.endDate }} {{ planDetail.status }}</div>
        <div class="duration-tag">{{ planDetail.duration }}</div>
      </div>

      <div class="tabs">
        <div class="tab-item" :class="{ active: activeTab === 'itinerary' }" @click="activeTab = 'itinerary'">行程</div>
        <div class="tab-item" :class="{ active: activeTab === 'expenses' }" @click="activeTab = 'expenses'">旅行账单</div>
        <div class="tab-item" :class="{ active: activeTab === 'luggage' }" @click="activeTab = 'luggage'">行李清单</div>
      </div>

      <template v-if="activeTab === 'itinerary'">
        <div v-for="day in days" :key="day.date" class="day-section">
          <div class="day-header" @click="day.isCollapsed = !day.isCollapsed">
            <div class="day-title">
              {{ day.date }} {{ day.weekDay }}
              <el-icon class="arrow-icon" :class="{ collapsed: !day.isCollapsed }"><ArrowRight /></el-icon>
            </div>
          </div>

          <!-- 折叠时不渲染地图：display:none 的容器宽高为 0，百度地图算不出正确视野 -->
          <div v-if="!day.isCollapsed" class="day-content">
            <!-- 路线属于当天行程本身：有 >=2 个带【地点名】标注的条目就自动串联，无需点击 -->
            <ItineraryRouteMap
              v-if="(routeStopsByDay[day.date] || []).length >= 2"
              :stops="routeStopsByDay[day.date]"
            />
            <div
              v-for="item in day.items"
              :key="item.id"
              class="itinerary-item"
              @click="showItemActions(day.date, item)">
              <div class="item-time">{{ formatTime(item.time) }}</div>
              <span class="item-type" :class="'item-type--' + (item.itemType || 'OTHER').toLowerCase()">
                {{ itemTypeLabel(item.itemType) }}
              </span>
              <div class="item-content">
                <!-- 按【景点名】把正文切段渲染：景点名是可点入口，其余是纯文本（不走 v-html） -->
                <template v-for="(seg, si) in contentSegments(item)" :key="si">
                  <a v-if="seg.spot" class="item-spot" @click.stop="openSpot(seg.spot)">{{ seg.text }}</a>
                  <span v-else>{{ seg.text }}</span>
                </template>
              </div>
            </div>

            <div class="add-item" @click="addItinerary(day.date)">
              <el-icon class="add-icon"><Plus /></el-icon>
              <span>添加行程</span>
            </div>
          </div>
        </div>
      </template>

      <template v-if="activeTab === 'expenses'">
        <div class="total-amount">
          <span class="total-label">总支出</span>
          <span class="total-value">¥{{ totalExpenses }}</span>
        </div>

        <div class="bill-list">
          <div v-for="bill in bills" :key="bill.id" class="list-item" @click="showBillActions(bill)">
            <div class="item-left">
              <div class="item-icon">
                <el-icon><component :is="bill.icon" /></el-icon>
              </div>
              <div class="item-info">
                <div class="item-title">{{ bill.title }}</div>
                <div class="item-date">{{ bill.date }}</div>
              </div>
            </div>
            <div class="item-amount">¥{{ bill.amount }}</div>
          </div>
        </div>
      </template>

      <template v-if="activeTab === 'luggage'">
        <div class="luggage-list">
          <div v-for="item in luggageList" :key="item.id" class="list-item" @click="showLuggageActions(item)">
            <div class="item-left">
              <div class="item-icon">
                <el-icon><component :is="item.icon" /></el-icon>
              </div>
              <div class="item-info">
                <div class="item-title">{{ item.name }}</div>
                <div class="item-date">{{ item.quantity }}个</div>
              </div>
            </div>
            <div class="item-amount">
              <span class="luggage-status" :class="item.isPacked ? 'status-packed' : 'status-unpacked'">
                {{ item.isPacked ? '已打包' : '未打包' }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 行程操作菜单 -->
    <div class="action-menu" v-if="showActions">
      <div class="action-item" @click="editItem">编辑</div>
      <div class="action-item delete" @click="deleteItem">删除</div>
      <div class="action-item cancel" @click="closeActions">取消</div>
    </div>

    <!-- 添加/编辑行程对话框 -->
    <el-dialog
      :title="isEditing ? '编辑行程' : '添加行程'"
      v-model="dialogVisible"
      width="90%">
      <div class="itinerary-form">
        <div class="form-item">
          <div class="form-label">行程类型</div>
          <div class="type-picker">
            <button
              v-for="t in ITEM_TYPES"
              :key="t.value"
              type="button"
              class="type-chip"
              :class="[{ active: currentItem.itemType === t.value }, 'type-chip--' + t.value.toLowerCase()]"
              @click="currentItem.itemType = t.value"
            >{{ t.label }}</button>
          </div>
        </div>
        <div class="form-item">
          <div class="form-label">选择时间</div>
          <el-time-select
            v-model="currentItem.time"
            start="06:00"
            step="00:15"
            end="23:45"
            placeholder="请选择时间"
            class="form-input">
          </el-time-select>
        </div>
        <div class="form-item">
          <div class="form-label">行程内容</div>
          <el-input
            v-model="currentItem.content"
            type="textarea"
            :rows="4"
            placeholder="请输入行程内容">
          </el-input>
        </div>
        <!-- 只有景点才有"点开详情 / 进路线串联"的语义，其余类型不需要填景点名 -->
        <div v-if="currentItem.itemType === 'SCENIC'" class="form-item">
          <div class="form-label">景点名称</div>
          <el-input
            v-model="currentItem.spotName"
            placeholder="如：黄鹤楼（留空则自动从内容里的【】读取）"
            class="form-input">
          </el-input>
          <p class="form-hint">填写后可在地图上串联路线，并点开查看景点介绍、游览建议与图片</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加加号按钮（仅在账单和行李清单 tab 时显示；操作菜单/弹窗打开时隐藏，避免压住菜单） -->
    <div class="add-button" v-if="activeTab !== 'itinerary' && !showActions && !showBillDialog && !showLuggageDialog" @click="showAddDialog">
      <el-icon><Plus /></el-icon>
    </div>

    <!-- 账单添加/编辑弹窗 -->
    <div class="bill-dialog" v-if="showBillDialog">
      <div class="bill-header">
        <div class="bill-title">{{ editingBillId !== null ? '编辑账单' : '添加账单' }}</div>
        <div class="bill-close-btn" @click="showBillDialog = false">
          <el-icon><Close /></el-icon>
        </div>
      </div>
      <div class="bill-input">
        <input type="text" v-model="billRemark" placeholder="点击填写备注..." class="remark-input">
        <div class="bill-amount">¥ {{ amount || '0' }}</div>
      </div>

      <div class="number-pad">
        <div
          v-for="key in numberKeys"
          :key="key"
          class="number-key"
          :class="{ 'complete-key': key === '完成' }"
          @click="inputNumber(key)">
          {{ key }}
        </div>
      </div>
    </div>

    <!-- 行李清单添加/编辑弹窗 -->
    <div class="luggage-dialog" v-if="showLuggageDialog">
      <div class="luggage-dialog-header">
        <div class="luggage-dialog-title">{{ editingLuggageId !== null ? '编辑物品' : '添加物品' }}</div>
        <div class="luggage-close-btn" @click="showLuggageDialog = false">
          <el-icon><Close /></el-icon>
        </div>
      </div>
      <input type="text"
             v-model="luggageName"
             placeholder="请输入物品名称"
             class="luggage-input"
             @keyup.enter="saveLuggage">
      <div class="luggage-field">
        <span class="luggage-label">数量</span>
        <input type="number"
               v-model.number="luggageQuantity"
               min="1"
               max="999"
               step="1"
               class="luggage-input luggage-input--inline">
      </div>
      <div class="luggage-field">
        <span class="luggage-label">已打包</span>
        <el-switch v-model="luggagePacked" />
      </div>
      <button class="luggage-confirm-btn" @click="saveLuggage">确定</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import ItineraryRouteMap from '../components/map/ItineraryRouteMap.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index.js'
import { extractStops } from '../utils/itineraryRoute'

const activeTab = ref('itinerary')
const planDetail = ref(null)
const days = ref([])
const router = useRouter()
// 全局正则用于切段，每次使用前必须重置 lastIndex
const POI_SEGMENT_RE = /【([^】]{1,40})】/g
// 行程类型：与后端 itemType 白名单一一对应，手动添加与 AI 添加共用同一套
const ITEM_TYPES = [
  { value: 'SCENIC', label: '景点' },
  { value: 'TRANSPORT', label: '交通' },
  { value: 'HOTEL', label: '住宿' },
  { value: 'DINING', label: '餐饮' },
  { value: 'OTHER', label: '其他' }
]
const ITEM_TYPE_LABELS = ITEM_TYPES.reduce((map, t) => {
  map[t.value] = t.label
  return map
}, {})

function itemTypeLabel(type) {
  return ITEM_TYPE_LABELS[type] || '其他'
}
const showActions = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const currentItem = reactive({
  id: null,
  content: '',
  date: null,
  time: '',
  itemType: 'SCENIC',
  spotName: ''
})
const selectedDate = ref(null)
const selectedItem = ref(null)
const showBillDialog = ref(false)
const amount = ref('')
const billRemark = ref('')
const numberKeys = ['1', '2', '3', '×', '4', '5', '6', '+', '7', '8', '9', '-', '.', '0', '=', '完成']
const bills = ref([])
const luggageList = ref([])
const showLuggageDialog = ref(false)
const luggageName = ref('')
const luggageQuantity = ref(1)
const luggagePacked = ref(false)
// 操作菜单的归属：行程/账单/行李共用同一个底部菜单与编辑删除入口
const actionType = ref('itinerary')
const selectedBill = ref(null)
const selectedLuggage = ref(null)
// null = 新增模式；有值 = 正在编辑该条记录（不再单独存 isEditing 布尔，避免两处状态不同步）
const editingBillId = ref(null)
const editingLuggageId = ref(null)

const expenseTypes = {
  accommodation: { label: '住宿', icon: 'House' },
  transportation: { label: '交通', icon: 'Van' },
  dining: { label: '餐饮', icon: 'KnifeFork' },
  shopping: { label: '购物', icon: 'ShoppingCart' },
  entertainment: { label: '娱乐', icon: 'VideoCamera' },
  scenic: { label: '景点', icon: 'Picture' },
  other: { label: '其他', icon: 'Goods' }
}

const totalExpenses = computed(() => {
  return bills.value.reduce((sum, bill) => sum + parseFloat(bill.amount), 0).toFixed(2)
})

// 把正文按【景点名】切成「文本 / 可点景点」片段。用切段渲染而不是 v-html：
// 正文来自模型输出，直接塞 HTML 就是 XSS 面；这里只产出纯文本节点。
function contentSegments(item) {
  const content = item.content || ''
  const segments = []
  let last = 0
  let match
  POI_SEGMENT_RE.lastIndex = 0
  while ((match = POI_SEGMENT_RE.exec(content)) !== null) {
    if (match.index > last) {
      segments.push({ text: content.slice(last, match.index) })
    }
    const name = match[1].trim()
    segments.push({ text: name, spot: name })
    last = match.index + match[0].length
  }
  if (last < content.length) {
    segments.push({ text: content.slice(last) })
  }
  if (!segments.length) {
    segments.push({ text: content })
  }
  return segments
}

function openSpot(name) {
  router.push({ path: '/spot-detail', query: { name } })
}

/**
 * 从景点详情返回时恢复滚动位置。
 * 路由切换会销毁本组件，页面高度瞬间塌成 0，浏览器随即把 scrollY 归零；
 * 等数据与地图重新渲染出来时，用户已经停在页面顶部了。
 */
const planScrollKey = 'wt:plan-scroll:' + (getUrlParam('id') || '')

function savePlanScroll() {
  try {
    sessionStorage.setItem(planScrollKey, String(window.scrollY || 0))
  } catch (e) {
    // 存储不可用（隐私模式）时忽略，退化为原来的行为
  }
}

function restorePlanScroll() {
  // 只在「从景点详情返回」时恢复；从列表新进计划时停在顶部才是正确的
  const back = window.history.state && window.history.state.back
  if (!back || !String(back).includes('spot-detail')) {
    return
  }
  let y = 0
  try {
    y = Number(sessionStorage.getItem(planScrollKey) || 0)
  } catch (e) {
    return
  }
  if (!y) {
    return
  }
  // 等行程条目与地图渲染完再滚：此时页面高度才够，否则 scrollTo 会被截断在山顶附近
  nextTick(() => {
    window.requestAnimationFrame(() => window.scrollTo(0, y))
  })
}

onBeforeUnmount(savePlanScroll)

// 按天把行程条目里的【地点名】提出来，交给内嵌地图自动串联。
// computed 缓存保证传给子组件的数组引用稳定，只有真正改动条目时才触发重画。
const routeStopsByDay = computed(() => {
  const result = {}
  days.value.forEach(day => {
    result[day.date] = extractStops(day.items)
  })
  return result
})
onMounted(() => {
  const planId = getUrlParam('id')
  if (planId) {
    loadPlanDetail(planId)
  }
  // 原 ?type= 参数仅用于区分账单/行李弹窗来源；SPA 中以当前 activeTab 为准
})

function loadPlanDetail(planId) {
  request.get(`/travel-plans/${planId}`)
    .then(res => {
      planDetail.value = res.data
      generateDays()
      loadExpenses(planId)
      loadLuggage(planId)
      loadItineraryItems(planId)
    })
    .catch(error => {
      console.error('加载计划详情失败:', error)
      ElMessage.error('加载计划详情失败')
    })
}

function loadExpenses(planId) {
  request.get(`/expenses/${planId}`)
    .then(res => {
      const data = res.data
      bills.value = (data || []).map(expense => ({
        id: expense.id,
        title: expense.title,
        amount: expense.amount,
        date: formatDate(new Date(expense.date)),
        remark: expense.remark,
        icon: (expenseTypes[expense.type] && expenseTypes[expense.type].icon) || 'Goods'
      }))
    })
    .catch(error => {
      console.error('加载账单失败:', error)
      ElMessage.error('加载账单失败')
    })
}

function loadLuggage(planId) {
  request.get(`/luggage-items/${planId}`)
    .then(res => {
      const data = res.data
      luggageList.value = (data || []).map(item => ({
        id: item.id,
        name: item.name,
        quantity: item.quantity,
        isPacked: item.isPacked,
        icon: 'Goods'
      }))
    })
    .catch(error => {
      console.error('加载行李清单失败:', error)
      ElMessage.error('加载行李清单失败')
    })
}

function loadItineraryItems(planId) {
  days.value.forEach(day => {
    day.items = []
  })

  request.get(`/itinerary-items/${planId}`)
    .then(res => {
      const data = res.data || []
      data.forEach(item => {
        const day = days.value.find(d => d.date === formatDate(new Date(item.date)))
        if (day) {
          day.items.push({
            id: item.id,
            content: item.content,
            spotName: item.spotName || '',
            itemType: item.itemType || '',
            date: formatDate(new Date(item.date)),
            time: item.time
          })
        }
      })

      days.value.forEach(day => {
        day.items.sort((a, b) => {
          return a.time.localeCompare(b.time)
        })
      })

      // 列表就位后再恢复滚动，避免时机太早导致页面高度不够
      restorePlanScroll()
    })
    .catch(error => {
      console.error('加载行程失败:', error)
      ElMessage.error('加载行程失败')
    })
}

function generateDays() {
  if (!planDetail.value) return

  const startDate = new Date(planDetail.value.startDate.replace(/\./g, '-'))
  const endDate = new Date(planDetail.value.endDate.replace(/\./g, '-'))
  const result = []

  for (let d = new Date(startDate); d <= endDate; d.setDate(d.getDate() + 1)) {
    result.push({
      date: formatDate(d),
      weekDay: getWeekDay(d),
      isCollapsed: false,
      items: []
    })
  }
  days.value = result
}

function formatDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}.${day}`
}

function getWeekDay(date) {
  const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekDays[date.getDay()]
}

function addItinerary(date) {
  isEditing.value = false
  currentItem.id = null
  currentItem.content = ''
  currentItem.date = date
  currentItem.itemType = 'SCENIC'
  currentItem.spotName = ''
  const now = new Date()
  currentItem.time = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')
  dialogVisible.value = true
}

function showItemActions(date, item) {
  actionType.value = 'itinerary'
  selectedDate.value = date
  selectedItem.value = item
  showActions.value = true
}

/** 账单条目：复用行程的操作菜单，交互与行程保持一致 */
function showBillActions(bill) {
  actionType.value = 'bill'
  selectedBill.value = bill
  showActions.value = true
}

/** 行李条目：复用行程的操作菜单，交互与行程保持一致 */
function showLuggageActions(item) {
  actionType.value = 'luggage'
  selectedLuggage.value = item
  showActions.value = true
}

function editItem() {
  if (actionType.value === 'bill') {
    editBill()
    return
  }
  if (actionType.value === 'luggage') {
    editLuggage()
    return
  }
  isEditing.value = true
  currentItem.id = selectedItem.value.id
  currentItem.content = selectedItem.value.content
  currentItem.date = selectedItem.value.date
  currentItem.time = selectedItem.value.time
  currentItem.itemType = selectedItem.value.itemType || 'OTHER'
  currentItem.spotName = selectedItem.value.spotName || ''
  dialogVisible.value = true
  showActions.value = false
}

function deleteItem() {
  if (actionType.value === 'bill') {
    deleteBill()
    return
  }
  if (actionType.value === 'luggage') {
    deleteLuggage()
    return
  }
  request.delete(`/itinerary-items/${selectedItem.value.id}`)
    .then(() => {
      const day = days.value.find(d => d.date === selectedDate.value)
      if (day) {
        const index = day.items.findIndex(item => item.id === selectedItem.value.id)
        if (index > -1) {
          day.items.splice(index, 1)
        }
      }
      ElMessage.success('删除成功')
    })
    .catch(error => {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    })
  showActions.value = false
}

function saveItem() {
  if (!currentItem.time) {
    ElMessage.warning('请选择时间')
    return
  }

  if (!currentItem.content.trim()) {
    ElMessage.warning('请输入行程内容')
    return
  }

  const dateParts = currentItem.date.split('.')
  const today = new Date()
  const formattedDate = `${today.getFullYear()}-${dateParts[0].padStart(2, '0')}-${dateParts[1].padStart(2, '0')}`

  const itemType = currentItem.itemType || 'OTHER'
  // 只有景点才需要景点名：优先用输入框，留空则从内容里的【】读取
  let spotName = ''
  if (itemType === 'SCENIC') {
    spotName = (currentItem.spotName || '').trim()
    if (!spotName) {
      POI_SEGMENT_RE.lastIndex = 0
      const match = POI_SEGMENT_RE.exec(currentItem.content)
      spotName = match ? match[1].trim() : ''
    }
  }
  let content = currentItem.content.trim()
  if (spotName && !content.startsWith('【')) {
    // 与 AI 写入的格式保持一致，地图与可点链接才认得出
    content = `【${spotName}】${content}`
  }

  const itemData = {
    planId: planDetail.value.id,
    date: formattedDate,
    time: typeof currentItem.time === 'string' ? currentItem.time : currentItem.time,
    content,
    spotName,
    itemType,
    title: content,
    status: 'pending'
  }

  if (isEditing.value) {
    request.put(`/itinerary-items/${currentItem.id}`, itemData)
      .then(() => {
        const day = days.value.find(d => d.date === currentItem.date)
        if (day) {
          const index = day.items.findIndex(item => item.id === currentItem.id)
          if (index > -1) {
            day.items.splice(index, 1, {
              id: currentItem.id,
              content: itemData.content,
              spotName,
              itemType,
              date: currentItem.date,
              time: itemData.time
            })
          }
        }
        dialogVisible.value = false
        ElMessage.success('编辑成功')
      })
      .catch(error => {
        console.error('保存失败:', error)
        ElMessage.error('保存失败')
      })
  } else {
    request.post(`/itinerary-items`, itemData)
      .then(() => {
        dialogVisible.value = false
        ElMessage.success('添加成功')
        loadItineraryItems(planDetail.value.id)
      })
      .catch(error => {
        console.error('保存失败:', error)
        ElMessage.error('保存失败')
      })
  }
}

function closeActions() {
  showActions.value = false
}

function showAddDialog() {
  if (activeTab.value === 'expenses') {
    editingBillId.value = null
    showBillDialog.value = true
    amount.value = ''
    billRemark.value = ''
  } else if (activeTab.value === 'luggage') {
    editingLuggageId.value = null
    showLuggageDialog.value = true
    luggageName.value = ''
    luggageQuantity.value = 1
    luggagePacked.value = false
  }
}

/** 编辑账单：回填备注与金额，日期沿用记录原值（后端未提交的字段保持不变） */
function editBill() {
  const bill = selectedBill.value
  editingBillId.value = bill.id
  billRemark.value = bill.remark || (bill.title === '未命名支出' ? '' : bill.title)
  amount.value = bill.amount != null ? String(bill.amount) : ''
  showActions.value = false
  showBillDialog.value = true
}

/** 编辑行李：回填名称、数量与打包状态 */
function editLuggage() {
  const item = selectedLuggage.value
  editingLuggageId.value = item.id
  luggageName.value = item.name
  luggageQuantity.value = item.quantity || 1
  luggagePacked.value = !!item.isPacked
  showActions.value = false
  showLuggageDialog.value = true
}

/** 删除账单：删除后重新拉取列表，总支出由列表重新汇总 */
function deleteBill() {
  const bill = selectedBill.value
  showActions.value = false
  request.delete(`/expenses/${bill.id}`)
    .then(() => {
      ElMessage.success('删除成功')
      loadExpenses(planDetail.value.id)
    })
    .catch(error => {
      console.error('删除账单失败:', error)
      ElMessage.error('删除账单失败')
    })
}

/** 删除行李 */
function deleteLuggage() {
  const item = selectedLuggage.value
  showActions.value = false
  request.delete(`/luggage-items/${item.id}`)
    .then(() => {
      ElMessage.success('删除成功')
      loadLuggage(planDetail.value.id)
    })
    .catch(error => {
      console.error('删除行李失败:', error)
      ElMessage.error('删除行李失败')
    })
}

function inputNumber(key) {
  if (key === '完成') {
    saveBill()
    return
  }

  if (key === '×') {
    amount.value = amount.value.slice(0, -1)
    return
  }

  if (key === '=' || key === '+' || key === '-') {
    return
  }

  if (key === '.' && amount.value.includes('.')) {
    return
  }

  if (amount.value.includes('.')) {
    const parts = amount.value.split('.')
    const decimal = parts[1]
    if (decimal && decimal.length >= 2) {
      return
    }
  }

  amount.value += key
}

/** 账单保存：新增走 POST，编辑走 PUT，两条路径共用同一份校验与回显逻辑 */
function saveBill() {
  if (!amount.value) {
    ElMessage.warning('请输入金额')
    return
  }

  const parsedAmount = parseFloat(amount.value)
  if (isNaN(parsedAmount) || parsedAmount <= 0) {
    ElMessage.warning('请输入正确的金额')
    return
  }

  if (!planDetail.value || !planDetail.value.id) {
    ElMessage.error('无法获取计划ID')
    return
  }

  const isEditing = editingBillId.value !== null
  const expenseData = {
    title: billRemark.value || '未命名支出',
    amount: parsedAmount,
    remark: billRemark.value
  }

  const submit = isEditing
    ? request.put(`/expenses/${editingBillId.value}`, expenseData)
    : request.post(`/expenses`, { ...expenseData, planId: planDetail.value.id, date: formatFullDate(new Date()) })

  submit
    .then(() => {
      ElMessage.success(isEditing ? '编辑成功' : '添加成功')
      showBillDialog.value = false
      amount.value = ''
      billRemark.value = ''
      loadExpenses(planDetail.value.id)
    })
    .catch(error => {
      console.error(isEditing ? '编辑账单失败:' : '添加账单失败:', error)
      ElMessage.error(isEditing ? '编辑账单失败' : '添加账单失败')
    })
}

/** 行李保存：新增走 POST，编辑走 PUT，字段与校验完全相同 */
function saveLuggage() {
  const name = (luggageName.value || '').trim()
  if (!name) {
    ElMessage.warning('请输入物品名称')
    return
  }

  const quantity = Number(luggageQuantity.value)
  if (!Number.isInteger(quantity) || quantity < 1) {
    ElMessage.warning('请输入正确的数量')
    return
  }

  if (!planDetail.value || !planDetail.value.id) {
    ElMessage.error('无法获取计划ID')
    return
  }

  const isEditing = editingLuggageId.value !== null
  const luggageData = {
    name,
    quantity,
    isPacked: luggagePacked.value
  }

  const submit = isEditing
    ? request.put(`/luggage-items/${editingLuggageId.value}`, luggageData)
    : request.post(`/luggage-items`, { ...luggageData, planId: planDetail.value.id })

  submit
    .then(() => {
      ElMessage.success(isEditing ? '编辑成功' : '添加成功')
      showLuggageDialog.value = false
      luggageName.value = ''
      luggageQuantity.value = 1
      luggagePacked.value = false
      loadLuggage(planDetail.value.id)
    })
    .catch(error => {
      console.error(isEditing ? '编辑行李失败:' : '添加行李失败:', error)
      ElMessage.error(isEditing ? '编辑行李失败' : '添加行李失败')
    })
}

/** 后端 date 列为 LocalDate，需要 YYYY-MM-DD */
function formatFullDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTime(timeString) {
  if (!timeString) return ''
  if (/^\d{2}:\d{2}$/.test(timeString)) {
    return timeString
  }
  try {
    if (timeString instanceof Date) {
      return timeString.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      })
    }
    return timeString.substring(0, 5)
  } catch (e) {
    console.error('时间格式化错误:', e)
    return timeString
  }
}
</script>

<style scoped>
/* 样式合并自原 清单详情.html <style> 内联块；body 全局重置不迁移 */
.detail-page {
  background-color: #f5f7fa;
  min-height: 100vh;
}
.detail-container {
  padding: 16px;
}
.date-info {
  margin-bottom: 20px;
}
.date-range {
  color: #409EFF;
  font-size: 15px;
  margin-bottom: 8px;
}
.duration-tag {
  display: inline-block;
  background: #ecf5ff;
  color: #409EFF;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 13px;
}
.tabs {
  margin-top: 20px;
  border-bottom: 1px solid #e4e7ed;
}
.tab-item {
  display: inline-block;
  padding: 8px 16px;
  font-size: 15px;
  color: #606266;
  cursor: pointer;
  position: relative;
}
.tab-item.active {
  color: #409EFF;
}
.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 100%;
  height: 2px;
  background: #409EFF;
}
.day-section {
  background: white;
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 1px;
}
.day-header {
  padding: 12px 16px;
  cursor: pointer;
}
.day-title {
  font-size: 16px;
  color: #303133;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.day-title .arrow-icon {
  transition: transform 0.3s;
}
.day-title .arrow-icon.collapsed {
  transform: rotate(90deg);
}
.day-content {
  padding: 0 16px 16px;
}
.itinerary-item {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.item-time {
  min-width: 60px;
  color: #409EFF;
  font-size: 14px;
  font-weight: 500;
}
/* 行程类型标签：景点/交通/住宿/餐饮/其他，一眼分清这行是干嘛的 */
.item-type {
  flex: none;
  align-self: flex-start;
  margin-top: 1px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}
.item-type--scenic { color: #409eff; background: #ecf5ff; }
.item-type--transport { color: #909399; background: #f4f4f5; }
.item-type--hotel { color: #8e44ad; background: #f5eef8; }
.item-type--dining { color: #e6a23c; background: #fdf6ec; }
.item-type--other { color: #909399; background: #f4f4f5; }
.item-content {
  flex: 1;
  margin-left: 12px;
  font-size: 14px;
  color: #303133;
}
/* 行程里的景点名：可点开该景点的 AI 详情（介绍/游览建议/站内实拍图） */
.item-spot {
  color: #409EFF;
  cursor: pointer;
  border-bottom: 1px dashed rgba(64, 158, 255, 0.55);
}
.item-spot:hover {
  color: #1a5f7a;
  border-bottom-color: #1a5f7a;
}
.add-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  color: #409EFF;
  cursor: pointer;
}
.add-icon {
  margin-right: 8px;
}
.action-menu {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 8px 0;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}
.action-item {
  padding: 16px;
  text-align: center;
  font-size: 16px;
  color: #303133;
}
.action-item.delete {
  color: #f56c6c;
}
.action-item.cancel {
  border-top: 8px solid #f5f7fa;
}
.add-button {
  position: fixed;
  right: 16px;
  bottom: 16px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: #1890ff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
  cursor: pointer;
  z-index: 1000;
  transition: all 0.3s ease;
}
.bill-dialog {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  border-radius: 20px 20px 0 0;
  z-index: 2000;
}
.bill-header {
  position: relative;
  padding: 16px;
  text-align: center;
  border-bottom: 1px solid #f5f7fa;
}
.bill-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}
.bill-close-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  cursor: pointer;
  font-size: 18px;
}
.bill-close-btn:hover {
  color: #606266;
}
.bill-input {
  padding: 16px;
  background: white;
  margin: 0 16px;
}
.remark-input {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 12px;
  width: 100%;
  box-sizing: border-box;
  font-size: 14px;
}
.bill-amount {
  font-size: 32px;
  text-align: right;
  padding: 8px;
  color: #333;
}
.number-pad {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: #f5f7fa;
  padding: 12px;
}
.number-key {
  background: white;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
}
.number-key:active {
  background: #f5f7fa;
}
.complete-key {
  background: #1890ff;
  color: white;
}
.bill-list,
.luggage-list {
  margin-top: 16px;
}
.list-item {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}
.item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.item-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f0f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
  font-size: 20px;
}
.item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.item-title {
  font-size: 16px;
  color: #303133;
}
.item-date {
  font-size: 12px;
  color: #909399;
}
.item-amount {
  font-size: 16px;
  color: #303133;
  font-weight: 500;
}
.total-amount {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.total-label {
  font-size: 14px;
  color: #606266;
}
.total-value {
  font-size: 20px;
  color: #f56c6c;
  font-weight: 500;
}
.luggage-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  margin-left: 8px;
}
.status-packed {
  background: #f0f9eb;
  color: #67c23a;
}
.status-unpacked {
  background: #f4f4f5;
  color: #909399;
}
.luggage-dialog {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  background: white;
  border-radius: 12px;
  padding: 20px;
  width: 80%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 2000;
}
.luggage-dialog-header {
  position: relative;
  margin-bottom: 16px;
}
.luggage-dialog-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  text-align: center;
}
.luggage-close-btn {
  position: absolute;
  right: -10px;
  top: -10px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  cursor: pointer;
  font-size: 18px;
}
.luggage-close-btn:hover {
  color: #606266;
}
.luggage-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 16px;
  box-sizing: border-box;
}
/* 与名称输入框同一套外观，仅在"数量"行内收窄、去掉多余下间距 */
.luggage-input--inline {
  width: 120px;
  margin-bottom: 0;
  text-align: right;
}
.luggage-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.luggage-label {
  font-size: 14px;
  color: #606266;
}
.luggage-confirm-btn {
  width: 100%;
  padding: 12px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}
.luggage-confirm-btn:active {
  background: #096dd9;
}
.itinerary-form {
  padding: 16px 0;
}
.form-item {
  margin-bottom: 16px;
}
.form-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}
.form-input {
  width: 100%;
}
.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
/* 类型选择：手点一排胶囊，比下拉更快，且颜色与列表标签一致 */
.type-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.type-chip {
  padding: 5px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 16px;
  background: #fff;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.15s;
}
.type-chip--scenic.active { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.type-chip--transport.active { border-color: #909399; color: #606266; background: #f4f4f5; }
.type-chip--hotel.active { border-color: #8e44ad; color: #8e44ad; background: #f5eef8; }
.type-chip--dining.active { border-color: #e6a23c; color: #b8791f; background: #fdf6ec; }
.type-chip--other.active { border-color: #909399; color: #606266; background: #f4f4f5; }
</style>
