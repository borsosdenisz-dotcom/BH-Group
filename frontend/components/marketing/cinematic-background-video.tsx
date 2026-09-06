"use client"

import { useEffect, useRef, useState } from "react"
import Image from "next/image"
import { cn } from "@/lib/utils"

interface NetworkInformation {
  saveData?: boolean
}

interface CinematicBackgroundVideoProps {
  /** MP4 source. When omitted, the poster image is the background and no <video> is ever mounted. */
  srcMp4?: string
  /** Optional WebM source, preferred by the browser over the MP4 when both are supported. */
  srcWebm?: string
  poster: string
  className?: string
  /** Applied only to the poster <Image>, e.g. a Ken Burns loop class. */
  posterClassName?: string
  overlayClassName?: string
  /** Above-the-fold hero usage: mounts the video immediately instead of waiting for intersection. */
  priority?: boolean
  /** Mobile viewports never mount the <video>, only the poster. Defaults to true. */
  mobileFallback?: boolean
}

/**
 * Decorative, muted, looping background video with a poster fallback.
 * Respects prefers-reduced-motion and navigator.connection.saveData (video
 * never mounts, poster stays as the background), and lazy-mounts non-priority
 * instances only once they near the viewport. If srcMp4 is omitted entirely,
 * this just renders the poster image - the same code path works today with
 * only images, and starts playing video the moment a real source is added.
 */
export function CinematicBackgroundVideo({
  srcMp4,
  srcWebm,
  poster,
  className,
  posterClassName,
  overlayClassName,
  priority = false,
  mobileFallback = true,
}: CinematicBackgroundVideoProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [videoAllowed, setVideoAllowed] = useState(false)
  const [videoInView, setVideoInView] = useState(priority)

  useEffect(() => {
    if (!srcMp4) return
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    const saveData = (navigator as Navigator & { connection?: NetworkInformation }).connection?.saveData
    const isMobile = mobileFallback && window.matchMedia("(max-width: 767px)").matches
    if (reduceMotion || saveData || isMobile) return
    setVideoAllowed(true)
  }, [srcMp4, mobileFallback])

  useEffect(() => {
    if (priority || !videoAllowed || videoInView) return
    const el = containerRef.current
    if (!el) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVideoInView(true)
          observer.disconnect()
        }
      },
      { rootMargin: "200px" }
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [priority, videoAllowed, videoInView])

  const showVideo = videoAllowed && videoInView && !!srcMp4

  return (
    <div ref={containerRef} aria-hidden="true" className={cn("absolute inset-0 overflow-hidden", className)}>
      <Image
        src={poster}
        alt=""
        fill
        sizes="100vw"
        priority={priority}
        className={cn("object-cover", posterClassName)}
      />
      {showVideo && (
        <video
          autoPlay
          muted
          loop
          playsInline
          preload="metadata"
          poster={poster}
          aria-hidden="true"
          tabIndex={-1}
          className="absolute inset-0 h-full w-full object-cover"
        >
          {srcWebm && <source src={srcWebm} type="video/webm" />}
          <source src={srcMp4} type="video/mp4" />
        </video>
      )}
      {overlayClassName && <div className={cn("absolute inset-0", overlayClassName)} />}
    </div>
  )
}
