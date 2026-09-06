"use client"

import { useState } from "react"
import Image from "next/image"
import { Building2, Images } from "lucide-react"
import { PhotoLightbox } from "@/components/booking/photo-lightbox"
import { cn } from "@/lib/utils"
import type { PropertyPhotoResponse } from "@/lib/api/types"

interface PropertyGalleryProps {
  photos: PropertyPhotoResponse[]
  propertyName: string
}

/**
 * Tailwind span classes for each thumbnail slot, keyed by how many
 * thumbnails actually exist (1-4) - so a property with fewer than 5 total
 * photos fills the grid cleanly instead of leaving empty tiles.
 */
function thumbnailSpan(thumbnailCount: number, index: number): string {
  if (thumbnailCount === 1) return "lg:col-span-2 lg:row-span-2"
  if (thumbnailCount === 2) return "lg:row-span-2"
  if (thumbnailCount === 3 && index === 2) return "lg:col-span-2"
  return ""
}

export function PropertyGallery({ photos, propertyName }: PropertyGalleryProps) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

  if (photos.length === 0) {
    return (
      <div className="flex aspect-[4/3] items-center justify-center rounded-2xl bg-muted text-muted-foreground sm:aspect-[16/9] lg:aspect-[2.2/1]">
        <Building2 className="size-10" />
      </div>
    )
  }

  const [hero, ...rest] = photos
  const thumbnails = rest.slice(0, 4)

  return (
    <div className="relative">
      <div
        className={cn(
          "grid gap-2 overflow-hidden rounded-2xl",
          thumbnails.length > 0 && "lg:h-[440px] lg:grid-cols-4 lg:grid-rows-2"
        )}
      >
        <button
          type="button"
          onClick={() => setLightboxIndex(0)}
          aria-label={`Deschide galeria foto — ${propertyName}, fotografia 1 din ${photos.length}`}
          className={cn(
            "group relative block aspect-[4/3] w-full overflow-hidden sm:aspect-[16/9] lg:aspect-auto lg:h-full",
            thumbnails.length > 0 ? "lg:col-span-2 lg:row-span-2" : "lg:aspect-[2.2/1]"
          )}
        >
          <Image
            src={hero.url}
            alt={hero.caption || propertyName}
            fill
            sizes="(min-width: 1024px) 50vw, 100vw"
            priority
            className="object-cover transition-transform duration-500 ease-out group-hover:scale-[1.03]"
          />
          <div className="absolute inset-0 bg-black/0 transition-colors duration-300 group-hover:bg-black/5" />
        </button>

        {thumbnails.map((photo, index) => (
          <button
            key={photo.id}
            type="button"
            onClick={() => setLightboxIndex(index + 1)}
            aria-label={`Deschide galeria foto — ${propertyName}, fotografia ${index + 2} din ${photos.length}`}
            className={cn(
              "group relative hidden overflow-hidden lg:block",
              thumbnailSpan(thumbnails.length, index)
            )}
          >
            <Image
              src={photo.url}
              alt={photo.caption || propertyName}
              fill
              sizes="25vw"
              className="object-cover transition-transform duration-500 ease-out group-hover:scale-[1.03]"
            />
            <div className="absolute inset-0 bg-black/0 transition-colors duration-300 group-hover:bg-black/5" />
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={() => setLightboxIndex(0)}
        className="absolute bottom-3 right-3 flex items-center gap-1.5 rounded-full border border-border/60 bg-background/90 px-3 py-1.5 text-xs font-medium shadow-sm backdrop-blur-sm transition-colors hover:bg-background sm:bottom-4 sm:right-4 sm:text-sm"
      >
        <Images className="size-3.5 sm:size-4" />
        Vezi toate fotografiile
        <span className="text-muted-foreground">· {photos.length}</span>
      </button>

      {lightboxIndex !== null && (
        <PhotoLightbox
          photos={photos}
          index={lightboxIndex}
          onIndexChange={setLightboxIndex}
          onClose={() => setLightboxIndex(null)}
          propertyName={propertyName}
        />
      )}
    </div>
  )
}
