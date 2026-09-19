<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthLayout from '@/components/AuthLayout.vue'
import { useUserStore } from '@/store/user'
import type { LoginParams } from '@/types/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<LoginParams>({
  username: '',
  password: '',
})

// 前端校验只为快速反馈。真正的校验在后端 —— 前端校验能被绕过，不能当安全措施。
const rules: FormRules<LoginParams> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  // validate() 校验不通过时会 reject，用 catch 统一收敛成 false
  const valid = await formRef.value.validate().catch(() => false)
  if (valid !== true) {
    return
  }

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')

    // 路由守卫记下的原始目标，登录后跳回去；没有就回首页
    const { redirect } = route.query
    await router.replace(typeof redirect === 'string' ? redirect : '/')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout title="登录你的工作台">
    <el-form
      ref="formRef"
      class="auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          size="large"
          placeholder="请输入用户名"
          autocomplete="username"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          size="large"
          type="password"
          placeholder="请输入密码"
          show-password
          autocomplete="current-password"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        class="submit"
        :loading="loading"
        native-type="submit"
      >
        登录
      </el-button>
    </el-form>

    <template #footer>
      还没有账号？
      <router-link to="/register">
        去注册
      </router-link>
    </template>
  </AuthLayout>
</template>

<style scoped>
.auth-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

/* label 收小一档、降为次级色，让输入框本身成为视觉主体 */
.auth-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: var(--wb-text-sm);
  line-height: 1.4;
  color: var(--wb-text-secondary);
}

.submit {
  width: 100%;
  margin-top: 6px;
}
</style>
