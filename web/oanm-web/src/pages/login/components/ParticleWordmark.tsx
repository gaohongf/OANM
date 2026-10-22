import { useEffect, useRef } from 'react'

import styles from '../Login.module.css'

const WORD = 'OANM'
const FONT_STACK =
  '"Segoe UI", "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif'
const FONT_SIZE = 240
const FONT_WEIGHT = 800

/**
 * 预渲染的光点 sprite。逐帧给每个粒子画渐变/设置 shadowBlur 会非常昂贵，
 * 这里只生成 4 张静态贴图，绘制时按颜色挑选、按透明度和尺寸贴上去。
 *
 * 第四档是更深一点的蓝，第三档是主色，第一档是"少量更亮"的冷白。
 */
const SPRITE_COLORS = [
  { rgb: '240, 246, 255', weight: 0.08 },
  { rgb: '220, 233, 255', weight: 0.22 },
  { rgb: '158, 197, 255', weight: 0.48 },
  { rgb: '110, 168, 255', weight: 0.22 },
] as const

const MOUSE_RADIUS = 128
const MOUSE_FORCE = 3.8
const SPRING = 0.022
const DAMPING = 0.9
const MAX_SPEED = 10
const INTRO_DURATION = 1350
const INTRO_DELAY = 350
const ALPHA_THRESHOLD = 120

interface HomePoint {
  x: number
  y: number
}

interface Particle extends HomePoint {
  vx: number
  vy: number
  startX: number
  startY: number
  homeX: number
  homeY: number
  offsetX: number
  offsetY: number
  amplitude: number
  phase: number
  speed: number
  delay: number
  size: number
  alpha: number
  sprite: number
}

/**
 * 由实时粒子组成的 OANM 字标。
 *
 * ## 字形从哪来
 * 不画路径、不读字体轮廓：把 OANM 用粗体画到离屏 Canvas 上，按 2px 步长读像素，
 * alpha 超过阈值的点就是粒子的 home。这样以后改字、改字重都不用碰采样逻辑。
 *
 * ## 为什么不是每个粒子都连一根线
 * 连线会把"粒子字"变成"星座图"，而且每次鼠标经过都会重排出一片乱麻。
 * 这里只保留光点本身和局部斥力，字形的可读性全靠粒子密度。
 *
 * ## 性能
 * - 粒子总数按视口分档（手机 1500 / 平板 2400 / 桌面 3600），不会无限堆。
 * - DPR 上限 2，避免 3x 屏上把像素量翻三倍。
 * - 页签不可见时取消 rAF；ResizeObserver 的尺寸重建做了 140ms 防抖。
 * - 不支持悬停的设备与 prefers-reduced-motion 用户只画一帧静态字形。
 */
export function ParticleWordmark() {
  const hostRef = useRef<HTMLDivElement | null>(null)
  const canvasRef = useRef<HTMLCanvasElement | null>(null)

  useEffect(() => {
    const host = hostRef.current
    const canvas = canvasRef.current
    if (!host || !canvas) {
      return
    }
    const context = canvas.getContext('2d')
    if (!context) {
      return
    }
    // 函数声明会被提升，TypeScript 不会把上面的 null 收窄带进闭包；
    // 这里显式落到非空别名上，后面的回调里直接用。
    const hostElement: HTMLDivElement = host
    const canvasElement: HTMLCanvasElement = canvas
    const drawingContext: CanvasRenderingContext2D = context

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const coarsePointer = window.matchMedia('(pointer: coarse)').matches
    const sprites = createSprites()

    let width = 0
    let height = 0
    let pixelRatio = 1
    let particles: Particle[] = []
    let mouse: { x: number; y: number } | null = null
    let intro = false
    let introStartedAt = 0
    let lastFrameAt = 0
    let rafId = 0
    let resizeTimer = 0
    let hasBuilt = false

    function draw() {
      drawingContext.clearRect(0, 0, width, height)
      for (const particle of particles) {
        const size = particle.size * 3
        drawingContext.globalAlpha = particle.alpha
        drawingContext.drawImage(
          sprites[particle.sprite],
          particle.x - size / 2,
          particle.y - size / 2,
          size,
          size,
        )
      }
      drawingContext.globalAlpha = 1
    }

    function update(now: number) {
      // 用相对 60fps 的缩放系数，让 120Hz 屏幕上的弹簧速度不会快一倍
      const delta = lastFrameAt === 0 ? 16.6667 : Math.min(32, now - lastFrameAt)
      lastFrameAt = now
      const frameScale = Math.max(0.25, Math.min(2, delta / 16.6667))

      if (intro) {
        const elapsed = now - introStartedAt
        let settled = true
        for (const particle of particles) {
          const progress = Math.min(
            1,
            Math.max(0, (elapsed - particle.delay) / INTRO_DURATION),
          )
          if (progress < 1) {
            settled = false
          }
          const eased = 1 - Math.pow(1 - progress, 3)
          particle.x =
            particle.startX + (particle.homeX + particle.offsetX - particle.startX) * eased
          particle.y =
            particle.startY + (particle.homeY + particle.offsetY - particle.startY) * eased
        }
        if (settled) {
          intro = false
        }
        return
      }

      const seconds = now / 1000
      // 这两个只和帧间隔有关，放到循环外算一次
      const springStep = SPRING * frameScale
      const damping = Math.pow(DAMPING, frameScale)
      for (const particle of particles) {
        // 呼吸只改目标点，不直接改位置：弹簧会把粒子柔和地带过去
        const homeX =
          particle.homeX +
          particle.offsetX +
          Math.sin(seconds * particle.speed + particle.phase) * particle.amplitude
        const homeY =
          particle.homeY +
          particle.offsetY +
          Math.cos(seconds * particle.speed * 0.8 + particle.phase) *
            particle.amplitude *
            0.8

        if (mouse) {
          const dx = particle.x - mouse.x
          const dy = particle.y - mouse.y
          const distanceSquared = dx * dx + dy * dy
          if (distanceSquared < MOUSE_RADIUS * MOUSE_RADIUS && distanceSquared > 0.01) {
            const distance = Math.sqrt(distanceSquared)
            const force =
              Math.pow(1 - distance / MOUSE_RADIUS, 2) * MOUSE_FORCE * frameScale
            particle.vx += (dx / distance) * force + (Math.random() - 0.5) * 0.3
            particle.vy += (dy / distance) * force + (Math.random() - 0.5) * 0.3
          }
        }

        particle.vx = (particle.vx + (homeX - particle.x) * springStep) * damping
        particle.vy = (particle.vy + (homeY - particle.y) * springStep) * damping

        const speed = Math.hypot(particle.vx, particle.vy)
        if (speed > MAX_SPEED) {
          particle.vx = (particle.vx / speed) * MAX_SPEED
          particle.vy = (particle.vy / speed) * MAX_SPEED
        }

        particle.x += particle.vx * frameScale
        particle.y += particle.vy * frameScale
      }
    }

    function frame(now: number) {
      update(now)
      draw()
      if (!document.hidden) {
        rafId = window.requestAnimationFrame(frame)
      }
    }

    function build(animate: boolean) {
      const nextWidth = hostElement.clientWidth
      const nextHeight = hostElement.clientHeight
      if (nextWidth < 2 || nextHeight < 2) {
        return
      }
      const nextRatio = Math.min(2, window.devicePixelRatio || 1)
      // ResizeObserver 在 observe 时会先回调一次；尺寸没变就不要重建，
      // 否则刚启动的聚合动画会被立刻按回 home。
      if (
        hasBuilt &&
        nextWidth === width &&
        nextHeight === height &&
        nextRatio === pixelRatio
      ) {
        return
      }

      hasBuilt = true
      width = nextWidth
      height = nextHeight
      pixelRatio = nextRatio
      canvasElement.width = Math.max(1, Math.round(width * pixelRatio))
      canvasElement.height = Math.max(1, Math.round(height * pixelRatio))
      drawingContext.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
      drawingContext.clearRect(0, 0, width, height)

      const shouldAnimate = animate && !reducedMotion
      const homes = pickHomes(sampleWord(width, height), targetParticleCount())
      particles = homes.map((home) => makeParticle(home, width, height, shouldAnimate))
      intro = shouldAnimate
      introStartedAt = performance.now()
      lastFrameAt = 0
      mouse = null

      if (reducedMotion) {
        draw()
      }
    }

    function scheduleBuild() {
      window.clearTimeout(resizeTimer)
      resizeTimer = window.setTimeout(() => build(false), 140)
    }

    function handlePointerMove(event: PointerEvent) {
      if (reducedMotion || coarsePointer) {
        return
      }
      const rect = canvasElement.getBoundingClientRect()
      mouse = { x: event.clientX - rect.left, y: event.clientY - rect.top }
    }

    function handlePointerLeave() {
      mouse = null
    }

    function handleVisibilityChange() {
      if (document.hidden) {
        window.cancelAnimationFrame(rafId)
        rafId = 0
        return
      }
      // 回到前台时重置时间戳，避免把后台停留的时长当成一帧
      if (!reducedMotion && rafId === 0) {
        lastFrameAt = 0
        rafId = window.requestAnimationFrame(frame)
      }
    }

    const observer = new ResizeObserver(scheduleBuild)
    observer.observe(hostElement)

    canvasElement.addEventListener('pointermove', handlePointerMove)
    canvasElement.addEventListener('pointerleave', handlePointerLeave)
    canvasElement.addEventListener('pointercancel', handlePointerLeave)
    window.addEventListener('blur', handlePointerLeave)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    build(true)
    if (!reducedMotion) {
      rafId = window.requestAnimationFrame(frame)
    }

    return () => {
      window.cancelAnimationFrame(rafId)
      window.clearTimeout(resizeTimer)
      observer.disconnect()
      canvasElement.removeEventListener('pointermove', handlePointerMove)
      canvasElement.removeEventListener('pointerleave', handlePointerLeave)
      canvasElement.removeEventListener('pointercancel', handlePointerLeave)
      window.removeEventListener('blur', handlePointerLeave)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

  return (
    <div ref={hostRef} className={styles.wordmark}>
      <canvas ref={canvasRef} className={styles.particleCanvas} aria-hidden="true" />
      <h1 className={styles.srOnly}>OANM</h1>
    </div>
  )
}

/* ---------- 采样与粒子构造 ---------- */

function sampleWord(width: number, height: number): HomePoint[] {
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(width))
  canvas.height = Math.max(1, Math.round(height))
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) {
    return []
  }

  const viewportWidth = window.innerWidth
  let maxWordWidth = width * 0.92
  if (viewportWidth >= 1100) {
    maxWordWidth = Math.min(maxWordWidth, viewportWidth * 0.48)
  } else if (viewportWidth >= 768) {
    maxWordWidth = Math.min(maxWordWidth, viewportWidth * 0.72)
  } else {
    maxWordWidth = Math.min(maxWordWidth, viewportWidth * 0.82)
  }
  const maxWordHeight = height * 0.62

  context.textAlign = 'left'
  context.textBaseline = 'alphabetic'

  let fontSize = FONT_SIZE
  context.font = `${FONT_WEIGHT} ${fontSize}px ${FONT_STACK}`
  let metrics = context.measureText(WORD)
  const measuredHeight = Math.max(
    1,
    (metrics.actualBoundingBoxAscent ?? fontSize * 0.72) +
      (metrics.actualBoundingBoxDescent ?? fontSize * 0.02),
  )
  const scale = Math.min(
    maxWordWidth / Math.max(1, metrics.width),
    maxWordHeight / measuredHeight,
  )
  fontSize = Math.max(36, fontSize * scale)

  context.font = `${FONT_WEIGHT} ${fontSize}px ${FONT_STACK}`
  metrics = context.measureText(WORD)
  const ascent = metrics.actualBoundingBoxAscent ?? fontSize * 0.72
  const descent = metrics.actualBoundingBoxDescent ?? fontSize * 0.02
  const textWidth = metrics.width
  const x = (canvas.width - textWidth) / 2
  const y = (canvas.height + ascent - descent) / 2

  context.fillStyle = '#ffffff'
  context.fillText(WORD, x, y)

  const data = context.getImageData(0, 0, canvas.width, canvas.height).data
  const points: HomePoint[] = []
  const step = 2
  for (let py = 0; py < canvas.height; py += step) {
    for (let px = 0; px < canvas.width; px += step) {
      if (data[(py * canvas.width + px) * 4 + 3] >= ALPHA_THRESHOLD) {
        points.push({ x: px + 0.5, y: py + 0.5 })
      }
    }
  }
  return points
}

function targetParticleCount(): number {
  const viewportWidth = window.innerWidth
  if (viewportWidth < 768) {
    return 1500
  }
  if (viewportWidth < 1100) {
    return 2400
  }
  return 3600
}

/**
 * 采样点按 2px 一格，数量通常高于目标。随机抽走一部分而不是等距抽取：
 * 等距抽取会在斜笔画上留下规则的横向条纹，看起来像扫描线。
 */
function pickHomes(points: HomePoint[], target: number): HomePoint[] {
  if (points.length <= target) {
    return points
  }
  for (let index = points.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    const temp = points[index]
    points[index] = points[swapIndex]
    points[swapIndex] = temp
  }
  points.length = target
  return points
}

function makeParticle(
  home: HomePoint,
  width: number,
  height: number,
  animate: boolean,
): Particle {
  const sprite = pickSpriteIndex()
  const startX = Math.random() * width
  const startY = Math.random() * height
  return {
    x: animate ? startX : home.x,
    y: animate ? startY : home.y,
    vx: 0,
    vy: 0,
    startX,
    startY,
    homeX: home.x,
    homeY: home.y,
    // ±1.5px 的自然抖动，避免采样点看起来像整齐的网格
    offsetX: (Math.random() - 0.5) * 3,
    offsetY: (Math.random() - 0.5) * 3,
    amplitude: 0.35 + Math.random() * 0.55,
    phase: Math.random() * Math.PI * 2,
    speed: 0.35 + Math.random() * 0.5,
    delay: Math.random() * INTRO_DELAY,
    size: Math.random() < 0.06 ? 1.9 + Math.random() * 0.3 : 1 + Math.random() * 1.2,
    // 冷白的那一档粒子更亮，占比也最少
    alpha: sprite === 0 ? 0.65 + Math.random() * 0.3 : 0.25 + Math.random() * 0.65,
    sprite,
  }
}

function pickSpriteIndex(): number {
  const roll = Math.random()
  let accumulated = 0
  for (let index = 0; index < SPRITE_COLORS.length; index += 1) {
    accumulated += SPRITE_COLORS[index].weight
    if (roll <= accumulated) {
      return index
    }
  }
  return SPRITE_COLORS.length - 1
}

function createSprites(): HTMLCanvasElement[] {
  return SPRITE_COLORS.map(({ rgb }) => {
    const canvas = document.createElement('canvas')
    canvas.width = 32
    canvas.height = 32
    const context = canvas.getContext('2d')
    if (!context) {
      return canvas
    }
    const gradient = context.createRadialGradient(16, 16, 0, 16, 16, 16)
    gradient.addColorStop(0, `rgba(${rgb}, 1)`)
    gradient.addColorStop(0.18, `rgba(${rgb}, 0.95)`)
    gradient.addColorStop(0.55, `rgba(${rgb}, 0.25)`)
    gradient.addColorStop(1, `rgba(${rgb}, 0)`)
    context.fillStyle = gradient
    context.beginPath()
    context.arc(16, 16, 16, 0, Math.PI * 2)
    context.fill()
    return canvas
  })
}
