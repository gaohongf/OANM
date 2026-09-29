import { useState } from 'react'
import { useNavigate } from 'react-router'
import { App as AntdApp, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'

import { useAuthStore } from '../store/auth'

const { Title, Text } = Typography

interface LoginForm {
  username: string
  password: string
}

/**
 * 登录页。
 *
 * 校验分两层：Form 的 rules 负责即时反馈（standard.md 要求"用户输入不可信，都需要校验"），
 * 后端仍然会独立校验一遍 —— 前端校验只是体验，不是防线。
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
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        background: 'linear-gradient(135deg, #f0f2f5 0%, #d9e4f5 100%)',
      }}
    >
      <Card className="page-transition" style={{ width: '100%', maxWidth: 380 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            OANM
          </Title>
          <Text type="secondary">运维与网络管理平台</Text>
        </div>

        <Form<LoginForm>
          name="login"
          size="large"
          initialValues={{ username: '', password: '' }}
          onFinish={onFinish}
          autoComplete="off"
        >
          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 2, max: 64, message: '用户名长度为 2 到 64 个字符' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" autoFocus />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, max: 20, message: '密码长度为 6 到 20 个字符' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
