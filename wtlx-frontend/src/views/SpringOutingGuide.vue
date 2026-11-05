<template>
  <div class="page-article">
    <HeaderBar title="找攻略" :show-back="true" />

    <div class="article-content">
      撒野去——春风捎来一封山野的来信📮用脚步丈量春天的颜色，开启一场不被打扰的踏青之旅🌿<br>·<br>
      ✔️婺源篁岭晒秋小径📍江西上饶（春季油菜花海）<br>
      古村依山而建，漫山梯田铺满金黄。错落屋檐下，花香与炊烟混合着春天的味道～穿汉服走在花间栈道，镜头一拍就是春日大片！<br>
      <img src="/imgs/content/篁岭花海.jpg" alt="篁岭油菜花海与古村" width="100%"><br>
      <br>·<br>
      ✔️洛阳王城公园赏牡丹📍河南洛阳（4月最佳）<br>
      “唯有牡丹真国色，花开时节动京城”。万株牡丹争相绽放，红、粉、紫、白层层叠叠，美得像极了春日信笺～建议清晨入园，避开人潮，拍下晨光中露珠未干的花颜！<br>
      <br>·<br>
      ✔️春日装备推荐：<br>
      ✅轻便草帽（遮阳+拍照神器）<br>
      ✅野餐垫&手账本（记录每一段花开的心情）<br>
      ✅手机长焦镜头（远处花景也能清晰收录）<br>
      <br>·<br>
      💡冷知识：篁岭的“晒秋”其实从春天就开始了，村民会将早春菜干挂在竹匾里晾晒，形成“五彩挂毯”，也是一种生活的仪式感～<br>
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
