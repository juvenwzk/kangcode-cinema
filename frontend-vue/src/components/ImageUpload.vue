<script setup>
/**
 * 图片上传组件（阿里云 OSS）
 *
 * 设计取舍：
 * 1. 自己发请求而不是用 el-upload 默认的 action —— 默认行为不带 Authorization 头，
 *    上传接口要登录态，用它就必须再挂 headers，不如直接接管 http-request。
 * 2. 只负责「传上去，把 URL 通过 v-model 抛给父组件」，落库由父组件在点保存时统一做。
 *    这样用户中途取消表单，库里不会留下半截数据。
 * 3. 父组件拿到的是完整 URL，直接塞进 <img src> 就能看。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Picture } from '@element-plus/icons-vue'
import { uploadApi } from '../api'

const props = defineProps({
  /** 图片 URL，v-model */
  modelValue: { type: String, default: '' },
  /** 业务目录：cinema / movie / avatar */
  dir: { type: String, default: 'common' },
  /** 预览框宽度 */
  width: { type: Number, default: 120 },
  /** 预览框高度 */
  height: { type: Number, default: 80 },
  /** 底部提示文案 */
  tip: { type: String, default: '支持 jpg/png/webp，单张不超过 5MB' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)

const boxStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`
}))

const MAX_SIZE = 5 * 1024 * 1024
const ALLOW = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

/** 上传前先拦一道：不合规的文件不必浪费一次网络请求 */
function beforeUpload(file) {
  if (!ALLOW.includes(file.type)) {
    ElMessage.error('只支持 jpg / png / webp / gif 格式')
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('图片不能超过 5MB，请先压缩')
    return false
  }
  return true
}

async function doUpload(options) {
  uploading.value = true
  try {
    const data = await uploadApi.image(options.file, props.dir)
    emit('update:modelValue', data.url)
    ElMessage.success('图片已上传')
  } catch (e) {
    // 拦截器已经弹过错误提示，这里只负责结束 loading
  } finally {
    uploading.value = false
  }
}

function clear() {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="image-upload">
    <el-upload
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="doUpload"
      :disabled="disabled || uploading"
      accept="image/jpeg,image/png,image/webp,image/gif"
    >
      <div v-loading="uploading" class="box" :style="boxStyle">
        <img v-if="modelValue" :src="modelValue" class="preview" alt="预览" />
        <div v-else class="placeholder">
          <el-icon :size="20"><Plus /></el-icon>
          <span>上传图片</span>
        </div>
        <div v-if="modelValue" class="mask">
          <el-icon><Picture /></el-icon>
          <span>点击替换</span>
        </div>
      </div>
    </el-upload>
    <div class="footer">
      <span class="tip">{{ tip }}</span>
      <el-button v-if="modelValue && !disabled" link type="danger" @click="clear">移除</el-button>
    </div>
  </div>
</template>

<style scoped>
.image-upload {
  display: inline-block;
  max-width: 100%;
}

.box {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--line, #d9d9d9);
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-2, #fafafa);
  cursor: pointer;
  transition: border-color 0.2s;
}

.box:hover {
  border-color: var(--el-color-primary);
}

.preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #fff;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--ink-3, #999);
  font-size: 12px;
}

.mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #fff;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.box:hover .mask {
  opacity: 1;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.tip {
  color: var(--ink-3, #999);
  font-size: 12px;
}
</style>
