"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { CheckCircle2 } from "lucide-react"
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
import { Slider } from "@/components/ui/slider"
import { useCreateLead } from "@/hooks/use-leads"
import { useUtmParams } from "@/hooks/use-utm-params"

const KNOWN_CITIES = ["Cluj-Napoca", "București", "Timișoara", "Brașov", "Iași"]

const estimateSchema = z.object({
  fullName: z.string().min(1, "Numele este obligatoriu").max(150),
  email: z.string().min(1, "Emailul este obligatoriu").email("Adresă de email invalidă"),
  phone: z.string().max(30).optional(),
  city: z.string().max(100).optional(),
  consentGiven: z.boolean().refine((v) => v, "Este necesar consimțământul pentru a fi contactat"),
})

type EstimateFormValues = z.infer<typeof estimateSchema>

export function RevenueEstimateSection() {
  const createLead = useCreateLead()
  const utm = useUtmParams()
  const [bedrooms, setBedrooms] = useState(2)
  const [honeypot, setHoneypot] = useState("")
  const [submitted, setSubmitted] = useState(false)

  const form = useForm<EstimateFormValues>({
    resolver: zodResolver(estimateSchema),
    defaultValues: { fullName: "", email: "", phone: "", city: KNOWN_CITIES[0], consentGiven: false },
  })

  function onSubmit(values: EstimateFormValues) {
    createLead.mutate(
      {
        fullName: values.fullName,
        email: values.email,
        phone: values.phone || undefined,
        city: values.city || undefined,
        leadType: "REVENUE_ESTIMATE",
        bedrooms,
        consentGiven: values.consentGiven,
        utmSource: utm.utmSource,
        utmMedium: utm.utmMedium,
        utmCampaign: utm.utmCampaign,
        website: honeypot,
      },
      { onSuccess: () => setSubmitted(true) }
    )
  }

  return (
    <section id="calculator" className="mx-auto max-w-6xl px-6 py-24 sm:px-10 sm:py-32">
      <div className="grid gap-12 lg:grid-cols-2 lg:items-center">
        <Reveal>
          <span className="text-sm font-medium text-primary">Estimare de venit</span>
          <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            Cât poți câștiga din proprietatea ta?
          </h2>
          <p className="mt-4 max-w-md text-balance text-muted-foreground">
            Lasă-ne câteva detalii despre proprietatea ta și îți trimitem o estimare personalizată,
            bazată pe locația exactă, dotări și sezon — nu o cifră generică.
          </p>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="rounded-3xl border border-border/60 bg-card p-8 shadow-sm">
            {submitted ? (
              <div className="flex flex-col items-center gap-3 py-6 text-center">
                <CheckCircle2 className="size-10 text-emerald-500" />
                <p className="font-medium">Mulțumim!</p>
                <p className="text-sm text-muted-foreground">
                  Îți trimitem o estimare personalizată în cel mai scurt timp, pe adresa de email
                  pe care ne-ai lăsat-o.
                </p>
              </div>
            ) : (
              <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5">
                  <HoneypotField name="website" value={honeypot} onChange={setHoneypot} />

                  <div>
                    <div className="flex items-center justify-between">
                      <label className="text-sm font-medium">Dormitoare</label>
                      <span className="text-sm text-muted-foreground">{bedrooms}</span>
                    </div>
                    <Slider
                      className="mt-3"
                      min={1}
                      max={5}
                      step={1}
                      value={[bedrooms]}
                      onValueChange={(v) => setBedrooms(Array.isArray(v) ? v[0] : v)}
                    />
                  </div>

                  <FormField
                    control={form.control}
                    name="city"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Oraș</FormLabel>
                        <FormControl>
                          <Input list="known-cities" placeholder="Cluj-Napoca" {...field} />
                        </FormControl>
                        <datalist id="known-cities">
                          {KNOWN_CITIES.map((c) => (
                            <option key={c} value={c} />
                          ))}
                        </datalist>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

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
                  <FormField
                    control={form.control}
                    name="phone"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Telefon (opțional)</FormLabel>
                        <FormControl>
                          <Input placeholder="07xx xxx xxx" {...field} />
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
                    {createLead.isPending ? "Se trimite..." : "Cere o estimare"}
                  </Button>
                </form>
              </Form>
            )}
          </div>
        </Reveal>
      </div>
    </section>
  )
}
