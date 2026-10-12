/**
 * vue-cropper 类型声明（通过 tsconfig.app.json 的 paths 映射接管）
 *
 * 背景：vue-cropper@1.1.4 的 package.json 中 `typings` 指向
 * `./lib/typings/index.d.ts`，该文件又链式 import 到 `lib/index.ts` 与
 * 未提供类型声明的 `lib/vue-cropper.vue`。由于包内发布的是 .ts 源码而非编译后的
 * .d.ts，skipLibCheck 无法跳过，会持续报 TS7016。
 *
 * 处理方式：在 tsconfig.app.json 中把模块 `vue-cropper` 用 paths 直接指向本文件，
 * 让 TS 不再去解析包内那份有问题的 typings。相比开启 allowJs 或放宽全局类型检查，
 * 这样既修掉报错，又保留了组件内 cropperRef 调用方法的类型提示。
 *
 * 导出与包内 `lib/index.ts` 的实际运行时导出一致：
 *   - 具名导出 VueCropper（组件，供 import { VueCropper } 使用）
 *   - 默认导出 globalCropper（含 install，可用于 app.use()）
 */
import type { DefineComponent, App } from 'vue'

/** 裁切器实例方法（供模板 ref 调用） */
export interface VueCropperInstance {
  /** 缩放：正数放大、负数缩小 */
  changeScale(num: number): void
  /** 向左旋转 */
  rotateLeft(): void
  /** 向右旋转 */
  rotateRight(): void
  /** 获取裁切后的 Blob */
  getCropBlob(callback: (blob: Blob) => void): void
  /** 获取裁切后的 base64 数据 */
  getCropData(callback: (data: string) => void): void
  /** 刷新画布 */
  refresh(): void
}

export const VueCropper: DefineComponent<
  Record<string, unknown>,
  Record<string, unknown>,
  unknown
>

export interface VueCropperGlobal {
  version: string
  install: (app: App) => void
  VueCropper: typeof VueCropper
}

declare const globalCropper: VueCropperGlobal
export default globalCropper