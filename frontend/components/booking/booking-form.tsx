"use client"

import Link from "next/link"
import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { CheckCircle2, Pencil, XCircle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useCreatePublicBooking, usePublicAvailability, usePublicQuote } from "@/hooks/use-public-booking"
import type { PublicBookingPayload } from "@/lib/api/public"
import type { PublicPropertyResponse, PublicReservationResponse } from "@/lib/api/types"

const bookingSchema = z.object({
  guestFirstName: z.string().min(1, "Prenumele este obligatoriu"),
  guestLastName: z.string().min(1, "Numele este obligatoriu"),
  guestEmail: z.string().min(1, "Emailul este obligatoriu").email("Adresă de email invalidă"),
  guestPhone: z.string().min(1, "Telefonul este obligatoriu"),
  checkInDate: z.string().min(1, "Data de check-in este obligatorie"),
  checkOutDate: z.string().min(1, "Data de check-out este obligatorie"),
  numberOfGuests: z.coerce.number().int().min(1),
  notes: z.string().optional(),
})

interface BookingFormProps {
  property: PublicPropertyResponse
  defaultCheckIn?: string
  defaultCheckOut?: string
  defaultGuests?: number
  onSuccess: (reservation: PublicReservationResponse) => void
}

function nights(checkIn: string, checkOut: string) {
  if (!checkIn || !checkOut) return 0
  const diff = new Date(checkOut).getTime() - new Date(checkIn).getTime()
  return Math.max(0, Math.round(diff / 86400000))
}

export function BookingForm({
  property,
  defaultCheckIn,
  defaultCheckOut,
  defaultGuests,
  onSuccess,
}: BookingFormProps) {
  const createBooking = useCreatePublicBooking()
  // Stable for the lifetime of this form instance: retries of the same
  // submission (slow network, accidental double-click) reuse the same key
  // so the backend returns the existing reservation instead of a duplicate.
  const [idempotencyKey] = useState(() => crypto.randomUUID())

  const form = useForm({
    resolver: zodResolver(bookingSchema),
    defaultValues: {
      guestFirstName: "",
      guestLastName: "",
      guestEmail: "",
      guestPhone: "",
      checkInDate: defaultCheckIn ?? "",
      checkOutDate: defaultCheckOut ?? "",
      numberOfGuests: defaultGuests ?? Math.min(2, property.maxGuests),
      notes: "",
    },
  })

  const [checkInDate, checkOutDate, numberOfGuests] = form.watch([
    "checkInDate",
    "checkOutDate",
    "numberOfGuests",
  ])
  const { data: availability, isFetching: isCheckingAvailability } = usePublicAvailability(
    property.id,
    checkInDate,
    checkOutDate
  )
  const { data: quote } = usePublicQuote(
    property.id,
    checkInDate,
    checkOutDate,
    Number(numberOfGuests) || 1
  )

  const stayNights = nights(checkInDate, checkOutDate)
  const datesLockedFromCalendar = !!defaultCheckIn && !!defaultCheckOut
  const editSelectionHref = `/book/${property.id}?checkIn=${checkInDate}&checkOut=${checkOutDate}&guests=${numberOfGuests}`

  function handleSubmit(values: z.infer<typeof bookingSchema>) {
    const payload: PublicBookingPayload = { ...values, propertyId: property.id, idempotencyKey }
    createBooking.mutate(payload, { onSuccess })
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Perioadă & oaspeți</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            {datesLockedFromCalendar ? (
              <div className="flex items-center justify-between gap-3 rounded-md border bg-muted/40 p-3 text-sm sm:col-span-2">
                <div>
                  <p className="font-medium">
                    {checkInDate} → {checkOutDate}
                  </p>
                  <p className="text-muted-foreground">{stayNights} nopți selectate</p>
                </div>
                <Link
                  href={editSelectionHref}
                  className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                >
                  <Pencil className="size-3" />
                  Modifică perioada
                </Link>
              </div>
            ) : (
              <>
                <FormField
                  control={form.control}
                  name="checkInDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Check-in</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="checkOutDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Check-out</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            )}
            <FormField
              control={form.control}
              name="numberOfGuests"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Oaspeți</FormLabel>
                  <Select
                    value={String(field.value ?? 1)}
                    onValueChange={(value) => value && field.onChange(Number(value))}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {Array.from({ length: property.maxGuests }, (_, i) => i + 1).map((n) => (
                        <SelectItem key={n} value={String(n)}>
                          {n} {n === 1 ? "oaspete" : "oaspeți"}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {checkInDate && checkOutDate && !isCheckingAvailability && availability && (
              <div className="space-y-2 sm:col-span-2">
                {availability.available ? (
                  <p className="flex items-center gap-1.5 text-sm text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 className="size-4" />
                    Disponibil — {stayNights} nopți
                  </p>
                ) : (
                  <p className="flex items-center gap-1.5 text-sm text-destructive">
                    <XCircle className="size-4" />
                    Indisponibil în perioada selectată.
                  </p>
                )}

                {quote?.available && quote.totalAmount != null && (
                  <div className="rounded-md border bg-muted/40 p-3 text-sm">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">
                        Subtotal ({quote.nights} nopți)
                      </span>
                      <span>
                        {quote.subtotal?.toLocaleString("ro-RO")} {quote.currency}
                      </span>
                    </div>
                    {!!quote.extraGuestFee && (
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">Taxă oaspeți suplimentari</span>
                        <span>
                          {quote.extraGuestFee.toLocaleString("ro-RO")} {quote.currency}
                        </span>
                      </div>
                    )}
                    {!!quote.cleaningFee && (
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">Taxă de curățenie</span>
                        <span>
                          {quote.cleaningFee.toLocaleString("ro-RO")} {quote.currency}
                        </span>
                      </div>
                    )}
                    {!!quote.discountAmount && (
                      <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                        <span>Discount ({quote.discountPercent}%)</span>
                        <span>
                          -{quote.discountAmount.toLocaleString("ro-RO")} {quote.currency}
                        </span>
                      </div>
                    )}
                    <div className="mt-1.5 flex justify-between border-t pt-1.5 font-medium text-foreground">
                      <span>Total</span>
                      <span>
                        {quote.totalAmount.toLocaleString("ro-RO")} {quote.currency}
                      </span>
                    </div>
                  </div>
                )}

                {quote && !quote.available && (
                  <p className="text-sm text-muted-foreground">{quote.unavailableReason}</p>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Datele tale de contact</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="guestFirstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Prenume</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="guestLastName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Nume</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="guestEmail"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email</FormLabel>
                  <FormControl>
                    <Input type="email" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="guestPhone"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Telefon</FormLabel>
                  <FormControl>
                    <Input type="tel" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem className="sm:col-span-2">
                  <FormLabel>Mesaj (opțional)</FormLabel>
                  <FormControl>
                    <Textarea rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
        </Card>

        <p className="text-xs text-muted-foreground">
          Prin trimiterea cererii ești de acord cu{" "}
          <Link href="/termeni-si-conditii" className="underline hover:text-foreground" target="_blank">
            Termenii și condițiile
          </Link>{" "}
          și{" "}
          <Link href="/confidentialitate" className="underline hover:text-foreground" target="_blank">
            Politica de confidențialitate
          </Link>
          .
        </p>
        <Button type="submit" size="lg" disabled={createBooking.isPending}>
          {createBooking.isPending ? "Se trimite..." : "Trimite cererea de rezervare"}
        </Button>
      </form>
    </Form>
  )
}
