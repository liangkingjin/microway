<template>
  <div class="page-article">
    <HeaderBar title="找攻略" :show-back="true" />

    <div class="article-content">
      秘境光影——拍照党的小众打卡天堂📷避开人潮，拍出电影感旅拍大片！<br>·<br>
      ✔️东极岛日出📍浙江舟山（人均花费300💰）<br>
      中国最早迎接太阳的地方！凌晨登山守候光影破晓，金色海面与渔船轮廓形成绝美剪影。推荐穿白色长裙+宽檐帽，海风拂面秒变海边文艺片女主！<br>
      <img src="/imgs/content/东极岛.jpg" alt="东极岛海边日出剪影" width="100%"><br>
      <br>·<br>
      ✔️丰宁大滩草原📍河北承德（适合夏秋季）<br>
      公路直通天边，牛羊低头吃草，氛围感拉满！日落时穿复古裙在草地奔跑，一定要用胶片滤镜拍～推荐平日错峰前往，整个草原都是你专属背景！<br>
      <br>·<br>
      ✔️塔川村秋色📍安徽黟县（摄影师心头爱）<br>
      古村徽派民居与红枫黄栌交错如油画，清晨水面倒影如梦似幻～拍照小技巧：趁晨雾未散，在村口桥头抓光影，特别出片！<br>
      <img src="/imgs/content/塔川秋色.jpg" alt="塔川村秋景" width="100%"><br>
      <br>·<br>
      ✔️装备推荐：<br>
      ✅偏振镜（去除反光，色彩更浓郁）<br>
      ✅复古相机包（道具+实用两不误）<br>
      ✅备用电池&三脚架（光影抓拍好帮手）<br>
      <br>·<br>
      💡冷知识：塔川村因秋色被BBC评为“中国十大最美乡村”，但村里没有星巴克，只有小茶馆和柴火饭，是真正“慢下来”的地方～<br>
    </div>
  </div>
</template>

<script setup>
import HeaderBar from '../components/HeaderBar.vue'
</script>

<style scoped>
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
