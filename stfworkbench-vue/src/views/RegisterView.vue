<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthLayout from '@/components/AuthLayout.vue'
import { useUserStore } from '@/store/user'
import type { RegisterParams } from '@/types/user'

/** 比接口多一个确认密码字段，它只在前端校验，不发给后端 */
interface RegisterForm extends RegisterParams {
  confirmPassword: string
}

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})

// 规则与 docs/接口清单.md §3 保持一致。
// 后端仍会独立校验一遍 —— 前端这层绕过去了也拦得住。
const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{4,50}$/,
      message: '只能由 4-50 位字母、数字或下划线组成',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度需为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (valid !== true) {
    return
  }

  loading.value = true
  try {
    // 只把该发的字段发出去，confirmPassword 留在前端
    await userStore.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
    })
    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login' })
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout title="创建你的账号">
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
          placeholder="4-50 位字母、数字或下划线"
          autocomplete="username"
        />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          size="large"
          type="password"
          placeholder="6-32 位"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          size="large"
          type="password"
          placeholder="请再次输入密码"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="昵称（可选）" prop="nickname">
        <el-input
          v-model="form.nickname"
          size="large"
          placeholder="不填则使用用户名"
        />
      </el-form-item>

      <el-button
        type="primary"
        size="large"
        class="submit"
        :loading="loading"
        native-type="submit"
      >
        注册
      </el-button>
    </el-form>

    <template #footer>
      已有账号？
      <router-link to="/login">
        去登录
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
