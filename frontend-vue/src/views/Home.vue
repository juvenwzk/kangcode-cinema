<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { cinemaApi, screeningApi } from '../api'

const router = useRouter()
const loading = ref(true)
const cinemas = ref([])
const screenings = ref([])

/** 无海报的影片用影片名生成稳定的渐变色占位 */
const GRADIENTS = [
  'linear-gradient(135deg,#ef4238,#f79b72)',
  'linear-gradient(135deg,#3b4a6b,#7f9cc0)',
  'linear-gradient(135deg,#2f7d6e,#8ad0b6)',
  'linear-gradient(135deg,#6b4a8c,#b997d6)',
  'linear-gradient(135deg,#b3862c,#e8c877)',
  'linear-gradient(135deg,#2b6b8f,#7cc2dd)'
]

function gradientOf(name) {
  let h = 0
  for (const ch of name || '') h = (h * 31 + ch.charCodeAt(0)) % 997
  return GRADIENTS[h % GRADIENTS.length]
}

/** 按影片聚合场次，海报取该影片第一张非空 posterUrl */
const movies = computed(() => {
  const map = new Map()
  for (const s of screenings.value) {
    const name = s.movieName || '未命名影片'
    if (!map.has(name)) {
      map.set(name, { name, posterUrl: s.posterUrl || '', count: 0, cinemas: new Set(), earliest: '99:99' })
    }
    const m = map.get(name)
    if (!m.posterUrl && s.posterUrl) m.posterUrl = s.posterUrl
    m.count += 1
    m.cinemas.add(s.cinemaId)
    if (s.startTime && s.startTime < m.earliest) m.earliest = s.startTime
  }
  return [...map.values()]
    .map((m) => ({ ...m, cinemaCount: m.cinemas.size }))
    .sort((a, b) => b.count - a.count)
})

const hotMovies = computed(() => movies.value.slice(0, 5))

const stats = computed(() => ({
  movies: movies.value.length,
  cinemas: cinemas.value.length,
  screenings: screenings.value.length
}))

const goMovie = (name) => router.push({ path: '/cinemas', query: { keyword: name } })

async function load() {
  loading.value = true
  try {
    const [cs, page] = await Promise.all([
      cinemaApi.list(),
      screeningApi.page({ page: 1, size: 200 })
    ])
    cinemas.value = cs || []
    screenings.value = page?.records || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="container home">
    <!-- 左侧：正在热映 -->
    <div class="main-col">
      <div class="section-title">
        <h2>正在热映 <span class="count">（{{ stats.movies }} 部）</span></h2>
        <el-button link type="primary" @click="router.push('/cinemas')">影院列表 ›</el-button>
      </div>

      <div v-loading="loading" class="movie-grid">
        <div v-for="m in movies" :key="m.name" class="movie-card">
          <div class="poster" :style="m.posterUrl ? {} : { background: gradientOf(m.name) }">
            <img v-if="m.posterUrl" :src="m.posterUrl" :alt="m.name" class="poster-img" loading="lazy" />
            <template v-else>
              <span class="poster-name">{{ m.name }}</span>
            </template>
            <span class="poster-badge">{{ m.count }} 场</span>
          </div>
          <div class="movie-info">
            <div class="movie-name" :title="m.name">{{ m.name }}</div>
            <div class="movie-meta">{{ m.cinemaCount }} 家影院 · 最早 {{ (m.earliest || '').slice(0, 5) }}</div>
          </div>
          <el-button class="buy-btn" type="primary" text @click="goMovie(m.name)">购票</el-button>
        </div>

        <el-empty v-if="!loading && !movies.length" description="暂无排片，去影院列表看看" />
      </div>
    </div>

    <!-- 右侧：概览 + 热门 -->
    <aside class="side-col">
      <div class="card side-card">
        <div class="side-title">数据概览</div>
        <div class="stat-row">
          <div class="stat"><span class="num">{{ stats.movies }}</span><span class="lbl">在映影片</span></div>
          <div class="stat"><span class="num">{{ stats.cinemas }}</span><span class="lbl">合作影院</span></div>
          <div class="stat"><span class="num">{{ stats.screenings }}</span><span class="lbl">排片场次</span></div>
        </div>
      </div>

      <div class="card side-card">
        <div class="side-title">热门影片</div>
        <ul class="rank-list">
          <li v-for="(m, i) in hotMovies" :key="m.name" @click="goMovie(m.name)">
            <span class="rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
            <span class="rank-name">{{ m.name }}</span>
            <span class="rank-count">{{ m.count }} 场</span>
          </li>
          <li v-if="!hotMovies.length" class="muted">暂无数据</li>
        </ul>
      </div>

      <div class="card side-card tip">
        <div class="side-title">说明</div>
        <p class="muted">
          数据来自后端 <code>/api/cinema/list</code> 与 <code>/api/screening/page</code>，
          海报存储于阿里云 OSS，按影片聚合展示。
        </p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  gap: 20px;
  padding-top: 8px;
  align-items: flex-start;
}

.main-col {
  flex: 1;
  min-width: 0;
}

.count {
  color: var(--ink-3);
  font-size: 13px;
  font-weight: 400;
}

.movie-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  min-height: 120px;
}

.movie-card {
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
  transition: transform 0.15s, box-shadow 0.15s;
}

.movie-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.poster {
  position: relative;
  height: 190px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}

.poster-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-name {
  color: #fff;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.35;
  text-align: center;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.poster-badge {
  position: absolute;
  left: 0;
  top: 0;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 11px;
  border-bottom-right-radius: 6px;
}

.movie-info {
  padding: 10px 12px 0;
}

.movie-name {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.movie-meta {
  color: var(--ink-3);
  font-size: 12px;
  margin-top: 4px;
}

.buy-btn {
  margin: 2px 4px 6px;
}

.side-col {
  flex: 0 0 280px;
}

.side-card {
  padding: 16px;
  margin-bottom: 16px;
}

.side-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  text-align: center;
}

.stat {
  flex: 1;
}

.stat .num {
  display: block;
  color: var(--el-color-primary);
  font-size: 22px;
  font-weight: 700;
}

.stat .lbl {
  color: var(--ink-3);
  font-size: 12px;
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rank-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
  cursor: pointer;
}

.rank-list li:last-child {
  border-bottom: none;
}

.rank-list li:hover .rank-name {
  color: var(--el-color-primary);
}

.rank {
  width: 18px;
  color: var(--ink-3);
  font-size: 13px;
  font-weight: 700;
}

.rank.top {
  color: var(--el-color-primary);
}

.rank-name {
  flex: 1;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-count {
  color: var(--ink-3);
  font-size: 12px;
}

.tip p {
  margin: 0;
  line-height: 1.7;
}

code {
  background: #f3f5f7;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
