import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import type { R } from '@/api/types/common'

/**
 * Axios 实例 + 拦截器
 * - baseURL 从环境变量读取（开发环境 http://localhost:1000/api，生产环境 /api）
 * - 响应拦截器自动剥离 R<T> 外壳，成功返回 data，失败弹 ElMessage
 * - 网络错误弹 ElNotification
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000
})

// 请求拦截器：附加 Token（鉴权落地后启用）
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // const token = useUserStore().token
    // if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理 R<T>
request.interceptors.response.use(
  (response) => {
    const res = response.data as R<unknown>
    if (res.code === 200) {
      // 成功直接返回 data，剥离 R<T> 外壳
      return res.data
    }
    // 业务错误：统一弹 Message
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    // 网络错误 / 超时 / 401 等
    if (error.response?.status === 401) {
      // 后续：跳转登录页
    }
    ElNotification.error({
      title: '请求错误',
      message: error.message || '网络异常'
    })
    return Promise.reject(error)
  }
)

/**
 * 封装 GET 请求，返回 Promise<T>（已剥离 R<T> 外壳）
 * 由于响应拦截器返回的是 res.data（unknown 类型），这里通过泛型断言为 T
 */
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, data, config) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.put(url, data, config) as unknown as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.delete(url, config) as unknown as Promise<T>
}

export default request
