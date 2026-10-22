import { useState } from 'react'
import { useNavigate } from 'react-router'
import { App as AntdApp, Button, Form, Input } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'

import { useAuthStore } from '../store/auth'
import { ParticleWordmark } from './login/components/ParticleWordmark'
import styles from './login/Login.module.css'

interface LoginForm {
  username: string
  password: string
}

/**
 * 登录页。
 *
 * 校验分两层：Form 的 rules 负责即时反馈（standard.md 要求"用户输入不可信，都需要校验"），
 * 后端仍然会独立校验一遍 —— 前端校验只是体验，不是防线。
 *
 * 视觉上只有粒子 OANM 一个焦点：页面其余部分都是低对比度的深色，
 * 登录面板靠右（窄屏则落到下方），不会盖住字形主体。
 */
export default function Login() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const login = useAuthStore((state) => state.login)
  const [submitting, setSubmitting] = useState(false)

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true)
    try {
      await login(values.username, values.password)
      // 不在这里指定跳哪个页面: 由 App 的 index 路由决定（第一个可见菜单），
      // 免得登录页和路由表各维护一份"默认落地页"。
      navigate('/', { replace: true })
    } catch (error) {
      // 后端的业务失败（密码错误等）会走到这里，message 是后端给的中文提示。
      // 不用把 error 直接展示: 它的 message 有可能是网络层的技术描述。
      message.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.brand}>
        <span className={styles.brandMark} aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
            <circle cx="12" cy="12" r="7.2" stroke="currentColor" strokeWidth="1.4" />
            <circle cx="12" cy="12" r="2.2" fill="currentColor" opacity="0.85" />
            <circle cx="18.6" cy="6.6" r="1.4" fill="currentColor" />
          </svg>
        </span>
        <span className={styles.brandText}>
          <span className={styles.brandName}>OANM</span>
          <span className={styles.brandDesc}>运维与网络管理平台</span>
        </span>
      </div>

      <div className={styles.layout}>
        <div className={styles.visual}>
          <ParticleWordmark />
        </div>

        <section className={styles.panel} aria-labelledby="login-title">
          <h2 id="login-title" className={styles.title}>
            登录工作台
          </h2>
          <p className={styles.subtitle}>使用你的账号继续</p>

          <Form<LoginForm>
            className={styles.form}
            name="login"
            layout="vertical"
            size="large"
            initialValues={{ username: '', password: '' }}
            onFinish={onFinish}
            autoComplete="off"
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 2, max: 10, message: '用户名长度为 2 到 10 个字符' },
              ]}
            >
              <Input
                className={styles.input}
                prefix={<UserOutlined />}
                placeholder="用户名"
                autoComplete="username"
                autoFocus
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 20, message: '密码长度为 6 到 20 个字符' },
              ]}
            >
              <Input.Password
                className={styles.input}
                prefix={<LockOutlined />}
                placeholder="密码"
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item className={styles.submitItem}>
              <Button
                className={styles.submit}
                type="primary"
                htmlType="submit"
                block
                loading={submitting}
                disabled={submitting}
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <p className={styles.note}>仅限授权人员访问</p>
        </section>
      </div>
    </main>
  )
}
