"use client"

import { useEffect, useRef } from "react"
import Image from "next/image"
import { ChevronLeft, ChevronRight, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogClose, DialogContent, DialogTitle } from "@/components/ui/dialog"

export interface LightboxPhoto {
  id: string
  url: string
  caption: string | null
}

interface PhotoLightboxProps {
  photos: LightboxPhoto[]
  index: number
  onIndexChange: (index: number) => void
  onClose: () => void
  propertyName: string
}

export function PhotoLightbox({ photos, index, onIndexChange, onClose, propertyName }: PhotoLightboxProps) {
  const photo = photos[index]
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "ArrowRight") {
        event.preventDefault()
        onIndexChange((index + 1) % photos.length)
      }
      if (event.key === "ArrowLeft") {
        event.preventDefault()
        onIndexChange((index - 1 + photos.length) % photos.length)
      }
    }
    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [index, photos.length, onIndexChange])

  if (!photo) return null

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        showCloseButton={false}
        initialFocus={closeButtonRef}
        className="inset-0 flex h-dvh max-h-none w-screen max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-none border-none bg-black p-0 sm:max-w-none sm:rounded-none"
      >
        <DialogTitle className="sr-only">
          {propertyName} — fotografia {index + 1} din {photos.length}
        </DialogTitle>

        <div className="relative min-h-0 flex-1">
          <Image
            src={photo.url}
            alt={photo.caption || `${propertyName} — fotografia ${index + 1}`}
            fill
            sizes="100vw"
            className="object-contain"
            priority
          />

          <DialogClose
            render={
              <Button
                ref={closeButtonRef}
                type="button"
                size="icon"
                variant="ghost"
                className="absolute right-[max(0.75rem,env(safe-area-inset-right))] top-[max(0.75rem,env(safe-area-inset-top))] size-11 bg-black/45 text-white hover:bg-black/70 hover:text-white sm:size-10"
                aria-label="Închide galeria"
              />
            }
          >
            <X className="size-5" aria-hidden="true" />
          </DialogClose>

          {photos.length > 1 && (
            <>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="absolute left-[max(0.5rem,env(safe-area-inset-left))] top-1/2 size-11 -translate-y-1/2 bg-black/45 text-white hover:bg-black/70 hover:text-white sm:left-4 sm:size-10"
                onClick={() => onIndexChange((index - 1 + photos.length) % photos.length)}
                aria-label="Fotografia anterioară"
              >
                <ChevronLeft className="size-6" aria-hidden="true" />
              </Button>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="absolute right-[max(0.5rem,env(safe-area-inset-right))] top-1/2 size-11 -translate-y-1/2 bg-black/45 text-white hover:bg-black/70 hover:text-white sm:right-4 sm:size-10"
                onClick={() => onIndexChange((index + 1) % photos.length)}
                aria-label="Fotografia următoare"
              >
                <ChevronRight className="size-6" aria-hidden="true" />
              </Button>
            </>
          )}
          <div className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 bg-gradient-to-t from-black/70 to-transparent px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-10 text-xs text-white/90 sm:px-6 sm:text-sm">
            <span role="status" aria-live="polite" aria-atomic="true">
              {index + 1} / {photos.length}
            </span>
            {photo.caption && <span className="max-w-[75%] truncate">{photo.caption}</span>}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
