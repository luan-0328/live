<template>
  <div class="app-wrapper">
    <AppHeader />
    <main class="main-content">
      <router-view :key="$route.path" />
    </main>
  </div>
</template>

<script setup>
import AppHeader from './components/AppHeader.vue'
</script>

<style scoped>
.app-wrapper {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.main-content {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 84px 16px 48px;
  box-sizing: border-box;
}
</style>
