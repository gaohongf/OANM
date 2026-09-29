import { useNavigate } from 'react-router'
import { Button, Result } from 'antd'

/**
 * 404。
 * <p>
 * 两种情况下会走到这里：路径真的不存在，或者菜单指向了一个不在白名单里的页面组件。
 * 后者更值得注意 —— 所以文案里点明了"可能是菜单配置的问题"，让排查方向不至于跑偏。
 */
export default function NotFound() {
  const navigate = useNavigate()

  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="你访问的地址没有对应的页面。如果是通过菜单点进来的，可能是菜单配置指向了一个不存在的页面。"
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          返回首页
        </Button>
      }
    />
  )
}
