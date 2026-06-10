import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AI智能文档助手',
      description: 'AI智能文档助手提供智能文档问答和AI超级智能体服务'
    }
  },
  {
    path: '/doc-qa',
    name: 'DocQA',
    component: () => import('../views/DocQA.vue'),
    meta: {
      title: 'AI智能文档助手 - 技术文档问答',
      description: 'AI智能文档助手基于RAG技术，为您的技术文档提供精准问答服务'
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI超级智能体 - AI智能文档助手',
      description: 'AI超级智能体是您的全能AI助手，能解答各类专业问题，提供精准建议和解决方案'
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录 - AI智能文档助手'
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: {
      title: '注册 - AI智能文档助手'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login' && to.path !== '/register') {
    next('/login')
  } else if (token && (to.path === '/login' || to.path === '/register')) {
    next('/')
  } else {
    next()
  }
})

export default router
