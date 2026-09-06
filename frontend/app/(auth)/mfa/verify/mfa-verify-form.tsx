"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { useVerifyMfaLogin, useVerifyMfaRecoveryLogin } from "@/hooks/use-auth"
import { useAuthStore } from "@/lib/stores/auth-store"

const codeSchema = z.object({
  code: z.string().regex(/^\d{6}$/, "Codul trebuie să aibă 6 cifre"),
})

const recoverySchema = z.object({
  code: z.string().min(1, "Codul de recuperare este obligatoriu"),
})

type FormValues = { code: string }

export function MfaVerifyForm() {
  const router = useRouter()
  const verifyMfaLogin = useVerifyMfaLogin()
  const verifyMfaRecoveryLogin = useVerifyMfaRecoveryLogin()
  const [useRecoveryCode, setUseRecoveryCode] = useState(false)
  // Checked once on mount — a successful verification clears the challenge
  // token as part of navigating away, which must not re-trigger this guard.
  const [hadChallengeToken] = useState(() => !!useAuthStore.getState().mfaChallengeToken)

  const form = useForm<FormValues>({
    resolver: zodResolver(useRecoveryCode ? recoverySchema : codeSchema),
    defaultValues: { code: "" },
  })

  useEffect(() => {
    if (!hadChallengeToken) {
      router.replace("/login")
    }
  }, [hadChallengeToken, router])

  function onSubmit(values: FormValues) {
    if (useRecoveryCode) {
      verifyMfaRecoveryLogin.mutate(values.code)
    } else {
      verifyMfaLogin.mutate(values.code)
    }
  }

  const isPending = verifyMfaLogin.isPending || verifyMfaRecoveryLogin.isPending

  return (
    <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
      <CardHeader>
        <CardTitle className="text-xl">Verificare în doi pași</CardTitle>
        <CardDescription>
          {useRecoveryCode
            ? "Introdu unul dintre codurile de recuperare primite la activarea 2FA."
            : "Introdu codul din aplicația de autentificare (Google Authenticator, Authy)."}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="code"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{useRecoveryCode ? "Cod de recuperare" : "Cod de verificare"}</FormLabel>
                  <FormControl>
                    {useRecoveryCode ? (
                      <Input placeholder="7K9QX-4NPV2" autoComplete="off" {...field} />
                    ) : (
                      <Input
                        inputMode="numeric"
                        maxLength={6}
                        placeholder="123456"
                        autoComplete="one-time-code"
                        {...field}
                      />
                    )}
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" className="w-full" disabled={isPending}>
              {isPending ? "Se verifică..." : "Confirmă"}
            </Button>
          </form>
        </Form>
        <button
          type="button"
          className="mt-4 text-sm text-muted-foreground underline underline-offset-2 hover:text-foreground"
          onClick={() => {
            setUseRecoveryCode((v) => !v)
            form.reset({ code: "" })
          }}
        >
          {useRecoveryCode
            ? "Folosește codul din aplicația de autentificare"
            : "Ai pierdut accesul la aplicația de autentificare? Folosește un cod de recuperare"}
        </button>
      </CardContent>
    </Card>
  )
}
