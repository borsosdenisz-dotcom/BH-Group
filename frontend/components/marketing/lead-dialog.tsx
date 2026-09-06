"use client"

import { useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import Link from "next/link"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { HoneypotField } from "@/components/marketing/honeypot-field"
import { useCreateLead } from "@/hooks/use-leads"
import { useUtmParams } from "@/hooks/use-utm-params"

const leadSchema = z.object({
  fullName: z.string().min(1, "Numele este obligatoriu").max(150),
  email: z.string().min(1, "Emailul este obligatoriu").email("Adresă de email invalidă"),
  phone: z.string().max(30).optional(),
  city: z.string().max(100).optional(),
  message: z.string().max(2000).optional(),
  consentGiven: z.boolean().refine((v) => v, "Este necesar consimțământul pentru a fi contactat"),
})

type LeadFormValues = z.infer<typeof leadSchema>

export function LeadDialog({ trigger }: { trigger: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [honeypot, setHoneypot] = useState("")
  const createLead = useCreateLead()
  const utm = useUtmParams()
  const form = useForm<LeadFormValues>({
    resolver: zodResolver(leadSchema),
    defaultValues: { fullName: "", email: "", phone: "", city: "", message: "", consentGiven: false },
  })

  function onSubmit(values: LeadFormValues) {
    createLead.mutate(
      {
        fullName: values.fullName,
        email: values.email,
        phone: values.phone || undefined,
        city: values.city || undefined,
        message: values.message || undefined,
        consentGiven: values.consentGiven,
        utmSource: utm.utmSource,
        utmMedium: utm.utmMedium,
        utmCampaign: utm.utmCampaign,
        website: honeypot,
      },
      {
        onSuccess: () => {
          form.reset()
          setOpen(false)
        },
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Listează-ți proprietatea</DialogTitle>
          <DialogDescription>
            Lasă-ne datele tale de contact — revenim cu o estimare de venit în cel mult 24h.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
            <HoneypotField name="website" value={honeypot} onChange={setHoneypot} />
            <FormField
              control={form.control}
              name="fullName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Nume complet</FormLabel>
                  <FormControl>
                    <Input placeholder="Ion Popescu" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email</FormLabel>
                  <FormControl>
                    <Input type="email" placeholder="nume@exemplu.ro" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="phone"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Telefon</FormLabel>
                    <FormControl>
                      <Input placeholder="07xx xxx xxx" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="city"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Oraș</FormLabel>
                    <FormControl>
                      <Input placeholder="Cluj-Napoca" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={form.control}
              name="message"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Mesaj (opțional)</FormLabel>
                  <FormControl>
                    <Textarea rows={3} placeholder="Detalii despre proprietate..." {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="consentGiven"
              render={({ field }) => (
                <FormItem>
                  <label className="flex items-start gap-2 text-xs text-muted-foreground">
                    <Checkbox checked={field.value} onCheckedChange={field.onChange} className="mt-0.5" />
                    <span>
                      Sunt de acord să fiu contactat de BH Group și am citit{" "}
                      <Link href="/confidentialitate" className="underline hover:text-foreground" target="_blank">
                        Politica de confidențialitate
                      </Link>
                      .
                    </span>
                  </label>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" className="w-full" disabled={createLead.isPending}>
              {createLead.isPending ? "Se trimite..." : "Trimite"}
            </Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
