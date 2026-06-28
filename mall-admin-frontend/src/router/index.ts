import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/product/category',
    children: [
      {
        path: 'product/category',
        name: 'ProductCategory',
        component: () => import('@/views/product/category/index.vue'),
        meta: { title: '分类管理', icon: 'Grid' }
      },
      {
        path: 'product/brand',
        name: 'ProductBrand',
        component: () => import('@/views/product/brand/index.vue'),
        meta: { title: '品牌管理', icon: 'PriceTag' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// afterEach：设置页面标题
router.afterEach((to) => {
  document.title = `${to.meta.title || '管理后台'} - 商城管理`
})

export default router
