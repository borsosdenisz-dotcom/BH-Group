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
        overlayClassName="z-50 bg-black/80 supports-backdrop-filter:backdrop-blur-sm"
        // A panel rather than a full-screen takeover: 80% of the viewport on a
        // desktop, centred by the base dialog's own transform (which is why
        // nothing here touches inset or translate). Phones get a little more,
        // because 80% of a small screen leaves the photo too small to read.
        className="flex h-[85vh] w-[92vw] max-w-none flex-col gap-0 overflow-hidden rounded-2xl border-none bg-neutral-950 p-0 ring-0 sm:h-[80vh] sm:w-[80vw] sm:max-w-[1400px]"
      >
        <DialogTitle className="sr-only">
          {propertyName} — fotografia {index + 1} din {photos.length}
        </DialogTitle>

        <div className="relative min-h-0 flex-1">
          <Image
            src={photo.url}
            alt={photo.caption || `${propertyName} — fotografia ${index + 1}`}
            fill
            // sizes has to match what the panel actually renders, or Next.js
            // serves a source scaled for the wrong width and the photo looks
            // soft. object-contain keeps the whole frame visible - letterboxed
            // against the panel rather than cropped.
            sizes="(min-width: 640px) 80vw, 92vw"
            quality={90}
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
                className="absolute right-3 top-3 size-11 rounded-full bg-black/55 text-white hover:bg-black/80 hover:text-white sm:size-10"
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
                className="absolute left-3 top-1/2 size-11 -translate-y-1/2 rounded-full bg-black/55 text-white hover:bg-black/80 hover:text-white sm:left-4 sm:size-10"
                onClick={() => onIndexChange((index - 1 + photos.length) % photos.length)}
                aria-label="Fotografia anterioară"
              >
                <ChevronLeft className="size-6" aria-hidden="true" />
              </Button>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="absolute right-3 top-1/2 size-11 -translate-y-1/2 rounded-full bg-black/55 text-white hover:bg-black/80 hover:text-white sm:right-4 sm:size-10"
                onClick={() => onIndexChange((index + 1) % photos.length)}
                aria-label="Fotografia următoare"
              >
                <ChevronRight className="size-6" aria-hidden="true" />
              </Button>
            </>
          )}
          <div className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 bg-gradient-to-t from-black/75 to-transparent px-4 pb-4 pt-10 text-xs text-white/90 sm:px-6 sm:text-sm">
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
