<template>
  <div class="meta-picker">
    <!-- 分类：整行可点，单选，点选即写回并关闭 -->
    <div class="picker-row" @click="openCategoryModal">
      <span class="field-lead">
        <span class="field-icon"><AppstoreOutlined /></span>
        分类
      </span>
      <span class="picker-value" :class="{ 'is-empty': !category }">
        {{ category || '选择分类' }}
      </span>
      <RightOutlined class="picker-arrow" />
    </div>

    <!-- 标签：整行可点，多选，点确定才写回 -->
    <div class="picker-row is-last" @click="openTagModal">
      <span class="field-lead">
        <span class="field-icon"><TagsOutlined /></span>
        标签
      </span>
      <span class="picker-value" :class="{ 'is-empty': !tags?.length }">
        {{ tagSummary }}
      </span>
      <RightOutlined class="picker-arrow" />
    </div>

    <!-- 分类选择 -->
    <a-modal
      v-model:open="categoryModalOpen"
      title="选择分类"
      :width="440"
      :footer="null"
      centered
      :body-style="{ padding: '4px 24px 24px' }"
    >
      <label class="picker-search">
        <SearchOutlined class="picker-search-icon" />
        <input v-model="categoryKeyword" class="picker-search-input" placeholder="搜索分类..." />
      </label>
      <div class="picker-grid">
        <button
          v-for="item in categoryChoices"
          :key="item"
          type="button"
          class="picker-chip"
          :class="{ 'is-active': category === item }"
          @click="selectCategory(item)"
        >
          {{ item }}
        </button>
      </div>
      <div v-if="!categoryChoices.length" class="picker-empty">没有找到匹配的分类</div>
    </a-modal>

    <!-- 标签选择 -->
    <a-modal
      v-model:open="tagModalOpen"
      title="选择标签"
      :width="440"
      centered
      :body-style="{ padding: '4px 24px 20px' }"
    >
      <template #footer>
        <a-button @click="tagModalOpen = false">取消</a-button>
        <a-button type="primary" @click="confirmTags">确定</a-button>
      </template>
      <label class="picker-search">
        <SearchOutlined class="picker-search-icon" />
        <input v-model="tagKeyword" class="picker-search-input" placeholder="搜索标签..." />
      </label>
      <div class="picker-grid">
        <button
          v-for="item in tagChoices"
          :key="item"
          type="button"
          class="picker-chip"
          :class="{ 'is-active': tagDraft.includes(item) }"
          @click="toggleTag(item)"
        >
          {{ item }}
        </button>
      </div>
      <div v-if="!tagChoices.length" class="picker-empty">没有找到匹配的标签，输入后可直接创建</div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  RightOutlined,
  SearchOutlined,
  TagsOutlined,
} from '@ant-design/icons-vue'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController.ts'

interface Props {
  category?: string
  tags?: string[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:category', value: string): void
  (e: 'update:tags', value: string[]): void
}>()

// ----- 选项数据：组件自行获取，调用方只需双向绑定 category / tags -----
const categoryList = ref<string[]>([])
const tagList = ref<string[]>([])

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagList.value = res.data.data.tagList ?? []
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取标签分类列表失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

/** 行内摘要：未选择标签时显示占位提示 */
const tagSummary = computed(() => {
  const tags = props.tags ?? []
  return tags.length ? tags.join(' · ') : '添加标签'
})

/** 按关键词过滤，未收录的关键词追加到末尾以便直接创建 */
const filterChoices = (list: string[], keyword: string) => {
  const kw = keyword.trim()
  if (!kw) {
    return list
  }
  const matched = list.filter((item) => item.includes(kw))
  return matched.includes(kw) ? matched : [...matched, kw]
}

// ----- 分类：单选 -----
const categoryModalOpen = ref(false)
const categoryKeyword = ref('')
const categoryChoices = computed(() => filterChoices(categoryList.value, categoryKeyword.value))

const openCategoryModal = () => {
  categoryKeyword.value = ''
  categoryModalOpen.value = true
}

const selectCategory = (value: string) => {
  emit('update:category', value)
  categoryModalOpen.value = false
}

// ----- 标签：多选，弹窗内先记草稿，点确定才写回 -----
const tagModalOpen = ref(false)
const tagKeyword = ref('')
const tagDraft = ref<string[]>([])
const tagChoices = computed(() => filterChoices(tagList.value, tagKeyword.value))

const openTagModal = () => {
  tagKeyword.value = ''
  tagDraft.value = [...(props.tags ?? [])]
  tagModalOpen.value = true
}

const toggleTag = (value: string) => {
  const index = tagDraft.value.indexOf(value)
  if (index > -1) {
    tagDraft.value.splice(index, 1)
  } else {
    tagDraft.value.push(value)
  }
}

const confirmTags = () => {
  emit('update:tags', [...tagDraft.value])
  tagModalOpen.value = false
}
</script>

<style scoped>
/* 字段名：单色图标 + 文字 */
.field-lead {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.field-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--bg-body);
  color: var(--text-secondary);
  font-size: 15px;
  transition: transform 0.15s ease;
}

/* 分类 / 标签：整行可点，右侧显示当前值 + 箭头 */
.picker-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
}

.picker-row.is-last {
  border-bottom: none;
}

.picker-row:hover .picker-value {
  color: var(--text-primary-light);
}

.picker-row:hover .picker-arrow {
  transform: translateX(2px);
}

.picker-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--text-primary-light);
  transition: color 0.15s ease;
}

.picker-value.is-empty {
  color: var(--text-disabled);
}

.picker-arrow {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--text-disabled);
  transition: transform 0.15s ease;
}

/* 选择弹窗：搜索框 + 三列可点选项 */
.picker-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  background: var(--bg-body);
}

.picker-search-icon {
  flex: 0 0 auto;
  font-size: 14px;
  color: var(--text-secondary);
}

.picker-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-primary-light);
}

.picker-search-input::placeholder {
  color: var(--text-disabled);
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  max-height: 320px;
  margin-top: 18px;
  overflow-y: auto;
}

.picker-chip {
  height: 40px;
  padding: 0 8px;
  border: none;
  border-radius: 10px;
  background: var(--bg-body);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background 0.15s ease, color 0.15s ease;
}

.picker-chip:hover {
  color: var(--text-primary-light);
}

.picker-chip.is-active {
  background: var(--accent);
  color: #fff;
}

.picker-empty {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-disabled);
}

/* 深色模式适配 */
html.dark .field-icon,
html.dark .picker-search,
html.dark .picker-chip {
  background: rgba(255, 255, 255, 0.08);
}

html.dark .picker-chip.is-active {
  background: var(--accent);
}
</style>