"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
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
import { Alert, AlertDescription } from "@/components/ui/alert"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { useResetPassword } from "@/hooks/use-auth"

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/

const schema = z.object({
  newPassword: z
    .string()
    .min(8, "Parola trebuie să aibă minim 8 caractere")
    .regex(passwordPattern, "Parola trebuie să conțină o literă mare, una mică și o cifră"),
})

type FormValues = z.infer<typeof schema>

function ResetPasswordForm() {
  const searchParams = useSearchParams()
  const token = searchParams.get("token")
  const resetPassword = useResetPassword()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: "" },
  })

  function onSubmit(values: FormValues) {
    if (!token) return
    resetPassword.mutate({ token, newPassword: values.newPassword })
  }

  if (!token) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Link-ul de resetare este invalid. Solicită unul nou din pagina de recuperare parolă.
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="newPassword"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Parolă nouă</FormLabel>
              <FormControl>
                <Input type="password" autoComplete="new-password" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" className="w-full" disabled={resetPassword.isPending}>
          {resetPassword.isPending ? "Se resetează..." : "Resetează parola"}
        </Button>
      </form>
    </Form>
  )
}

export function ResetPasswordView() {
  return (
    <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
      <CardHeader>
        <CardTitle className="text-xl">Resetează parola</CardTitle>
        <CardDescription>Alege o parolă nouă pentru contul tău.</CardDescription>
      </CardHeader>
      <CardContent>
        <Suspense fallback={null}>
          <ResetPasswordForm />
        </Suspense>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          <Link href="/login" className="font-medium text-foreground hover:underline">
            Înapoi la autentificare
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
