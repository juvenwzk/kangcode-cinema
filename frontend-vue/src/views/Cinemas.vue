<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cinemaApi, screeningApi } from '../api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const cinemas = ref([])
const screenings = ref([])

const brand = ref('全部')
const city = ref('全部')
const keyword = ref(route.query.keyword || '')
const sort = ref('default')

// 首页点某部影片进来时，用 query.keyword 预填搜索
watch(
  () => route.query.keyword,
  (v) => {
    keyword.value = v || ''
  }
)

/** 影院名形如「万达影城·天河店」，取「·」前的部分作为品牌 */
const brandOf = (name) => (name || '').split('·')[0]

const brandOptions = computed(() => [
  '全部',
  ...[...new Set(cinemas.value.map((c) => brandOf(c.cinemaName)).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b, 'zh-Hans-CN')
  )
])

const cityOptions = computed(() => [
  '全部',
  ...new Set(cinemas.value.map((c) => c.city).filter(Boolean))
])

const enriched = computed(() =>
  cinemas.value.map((c) => {
    const list = screenings.value.filter((s) => s.cinemaId === c.id)
    return {
      ...c,
      brand: brandOf(c.cinemaName),
      count: list.length,
      movies: [...new Set(list.map((s) => s.movieName).filter(Boolean))]
    }
  })
)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  let list = enriched.value.filter((c) => {
    if (brand.value !== '全部' && c.brand !== brand.value) return false
    if (city.value !== '全部' && c.city !== city.value) return false
    if (kw) {
      const hit =
        (c.cinemaName || '').toLowerCase().includes(kw) ||
        c.movies.some((m) => (m || '').toLowerCase().includes(kw))
      if (!hit) return false
    }
    return true
  })
  if (sort.value === 'more') list = [...list].sort((a, b) => b.count - a.count)
  if (sort.value === 'less') list = [...list].sort((a, b) => a.count - b.count)
  return list
})

async function load() {
  loading.value = true
  try {
    const [list, page] = await Promise.all([
      cinemaApi.list(),
      screeningApi.page({ page: 1, size: 200 })
    ])
    cinemas.value = list || []
    screenings.value = page?.records || []
  } finally {
    loading.value = false
  }
}

const go = (id) => router.push(`/cinemas/${id}`)

onMounted(load)
</script>

<template>
  <div class="container">
    <!-- 筛选区 -->
    <div class="card filter-panel">
      <div class="filter-row">
        <span class="filter-label">品牌</span>
        <div class="filter-items">
          <span
            v-for="b in brandOptions"
            :key="b"
            class="filter-item"
            :class="{ active: brand === b }"
            @click="brand = b"
          >{{ b }}</span>
        </div>
      </div>

      <div class="filter-row">
        <span class="filter-label">城市</span>
        <div class="filter-items">
          <span
            v-for="c in cityOptions"
            :key="c"
            class="filter-item"
            :class="{ active: city === c }"
            @click="city = c"
          >{{ c }}</span>
        </div>
      </div>

      <div class="filter-row">
        <span class="filter-label">搜索</span>
        <div class="filter-items search-line">
          <el-input
            v-model="keyword"
            placeholder="按影院名或影片名搜索"
            clearable
            size="small"
            style="width: 260px"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <span class="muted" style="margin-left: 12px">共 {{ filtered.length }} 家影院</span>
        </div>
      </div>
    </div>

    <!-- 列表标题 -->
    <div class="section-title">
      <h2>影院列表</h2>
      <el-select v-model="sort" size="small" style="width: 120px">
        <el-option label="默认排序" value="default" />
        <el-option label="场次多优先" value="more" />
        <el-option label="场次少优先" value="less" />
      </el-select>
    </div>

    <!-- 影院列表 -->
    <div v-loading="loading" class="card cinema-list">
      <div v-for="c in filtered" :key="c.id" class="cinema-row">
        <!-- 影院 Logo：没上传时用品牌首字兜底，避免列表出现一排破图 -->
        <div class="cinema-logo">
          <img v-if="c.logoUrl" :src="c.logoUrl" :alt="c.cinemaName" loading="lazy" />
          <span v-else>{{ c.brand.slice(0, 1) }}</span>
        </div>

        <div class="cinema-main">
          <div class="cinema-name">{{ c.cinemaName }}</div>
          <div class="cinema-meta">
            <el-icon><Location /></el-icon>
            {{ c.city || '—' }}
            <span class="dot">·</span>
            共 {{ c.count }} 个场次
          </div>
          <div class="cinema-tags">
            <el-tag
              v-for="m in c.movies.slice(0, 4)"
              :key="m"
              size="small"
              effect="plain"
              type="info"
            >{{ m }}</el-tag>
            <el-tag v-if="c.movies.length > 4" size="small" effect="plain" type="info">
              +{{ c.movies.length - 4 }}
            </el-tag>
            <el-tag v-if="!c.movies.length" size="small" effect="plain" type="warning">暂无排片</el-tag>
          </div>
        </div>

        <div class="cinema-side">
          <div class="side-count">
            <span class="num">{{ c.count }}</span>
            <span class="unit">场</span>
          </div>
          <el-button type="primary" round :disabled="!c.count" @click="go(c.id)">
            选座购票
          </el-button>
        </div>
      </div>

      <el-empty
        v-if="!loading && !filtered.length"
        description="没有符合条件的影院，换个筛选条件试试"
      />
    </div>
  </div>
</template>

<style scoped>
.cinema-list {
  padding: 6px 20px;
}

.cinema-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.cinema-row:last-child {
  border-bottom: none;
}

.cinema-main {
  flex: 1;
  min-width: 0;
}

.cinema-logo {
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2, #f5f5f5);
  color: var(--ink-3, #999);
  font-size: 22px;
  font-weight: 700;
}

.cinema-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #fff;
}

.cinema-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.cinema-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
  font-size: 13px;
  margin-bottom: 8px;
}

.dot {
  margin: 0 2px;
}

.cinema-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cinema-side {
  flex: 0 0 130px;
  text-align: center;
}

.side-count {
  margin-bottom: 8px;
  color: var(--ink-3);
}

.side-count .num {
  color: var(--el-color-primary);
  font-size: 22px;
  font-weight: 700;
}

.side-count .unit {
  font-size: 13px;
  margin-left: 2px;
}

.search-line {
  display: flex;
  align-items: center;
}
</style>
