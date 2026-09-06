"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Reveal } from "@/components/marketing/reveal"
import { HoneypotField } from "@/components/marketing/honeypot-field"
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
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
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

export function LeadFormSection() {
  const createLead = useCreateLead()
  const utm = useUtmParams()
  const [honeypot, setHoneypot] = useState("")
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
      { onSuccess: () => form.reset() }
    )
  }

  return (
    <section className="border-t border-border/60 bg-muted/30 py-24 sm:py-32">
      <div className="mx-auto max-w-2xl px-6 sm:px-10">
        <Reveal className="text-center">
          <span className="text-sm font-medium text-primary">Hai să vorbim</span>
          <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            Lasă-ne datele tale, te contactăm noi
          </h2>
          <p className="mt-4 text-balance text-muted-foreground">
            Completează formularul de mai jos — revenim cu o estimare de venit,
            fără niciun angajament din partea ta.
          </p>
        </Reveal>

        <Reveal delay={0.1} className="mt-10 rounded-3xl border border-border/60 bg-card p-8 shadow-sm">
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
              <HoneypotField name="website" value={honeypot} onChange={setHoneypot} />
              <div className="grid gap-4 sm:grid-cols-2">
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
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
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
                      <FormLabel>Oraș proprietate</FormLabel>
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
                      <Textarea rows={4} placeholder="Detalii despre proprietate..." {...field} />
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
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={field.onChange}
                        className="mt-0.5"
                      />
                      Sunt de acord să fiu contactat de BH Group în legătură cu această solicitare.
                    </label>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" size="lg" className="w-full" disabled={createLead.isPending}>
                {createLead.isPending ? "Se trimite..." : "Trimite datele"}
              </Button>
            </form>
          </Form>
        </Reveal>
      </div>
    </section>
  )
}
