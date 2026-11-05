<template>
  <div class="page-article">
    <HeaderBar title="找攻略" :show-back="true" />

    <div class="article-content">
      赏樱时节——东京大阪六日游🌸春日浪漫大赏！<br>·<br>
      ✔️必打卡：大阪城公园（免费）<br>
      登临天守阁，俯瞰樱海翻涌，古城与樱花交织的画卷令人心驰神往！早晨6点光影最佳，拍摄樱花雨效果绝美，强烈建议自备三脚架！<br>
      <img src="/imgs/content/大坂城樱花.jpg" alt="大阪城樱花盛放画面" width="100%"><br>
      <br>·<br>
      ✔️和风体验：浅草寺和服樱花行（租赁5000💴起）<br>
      穿上和服，漫步在红灯笼与樱花之间，仿佛走入时光长廊。还能体验抽签、拜神、祈福仪式，感受日式春日文化！<br>
      <img src="/imgs/content/浅草寺和服.jpg" alt="浅草寺和服与樱花" width="100%"><br>
      <br>·<br>
      ✔️夜樱浪漫：目黑川灯光秀（每日17:30亮灯）<br>
      粉色灯海与倒映樱花交织如梦，情侣牵手静赏夜樱，镜头随便一拍就是电影感！每晚整点还有光影演出～<br>
      <img src="/imgs/content/目黑川夜樱.jpg" alt="目黑川夜晚樱花灯光秀" width="100%"><br>
      <br>·<br>
      ✔️舌尖赏樱：樱花限定美食季🌸🍱<br>
      樱花大福Q弹爆浆，抹茶冰淇淋加入樱花碎片清新不腻，「赏樱便当」搭配粉色米饭和季节鱼片，吃出春天的味道！<br>
      <img src="/imgs/content/樱花美食.jpg" alt="樱花季限定日式美食" width="100%"><br>
      <br>·<br>
      ✔️交通TIPS：东京⇄大阪新干线（2.5小时）<br>
      推荐购买【JR Pass全国券】，无限次搭乘新干线+市内JR线超划算！列车沿途窗外樱花飞掠，感受“飞驰的春日”。<br>
      <br>·<br>
      ✔️旅行必备：<br>
      ✅轻便双肩包（可装自拍杆）<br>
      ✅移动WiFi or eSIM（实时直播樱花雨）<br>
      ✅口罩 & 抗过敏药（花粉季敏感预防）<br>
      <br>·<br>
      📍集合建议：<br>
      初日入住东京新宿站附近，交通便利、美食商圈丰富，翌日一早直达大阪赏樱最省心！<br>
      <br>·<br>
      💡冷知识：<br>
      你知道吗？染井吉野樱是日本樱花中最常见的一种，但几乎不会结果，全靠人工培育，花期一瞬，却是匠人一年辛苦的结晶～<br>
      <img src="/imgs/content/东京樱花冷知识.jpg" alt="染井吉野樱花特写" width="100%"><br>
    </div>
  </div>
</template>

<script setup>
import HeaderBar from '../components/HeaderBar.vue'
</script>

<style scoped>
/* 原 page1.html 无专属 CSS（.article-content 无全局定义），补最小可读样式 */
.page-article {
  background: #fff;
  min-height: 100vh;
}
.article-content {
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}
.article-content img {
  display: block;
  margin: 8px 0;
  border-radius: 8px;
}
</style>
