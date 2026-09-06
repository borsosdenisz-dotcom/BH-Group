"use client"

import Link from "next/link"
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
import { useForgotPassword } from "@/hooks/use-auth"

const schema = z.object({
  email: z.string().min(1, "Emailul este obligatoriu").email("Adresă de email invalidă"),
})

type FormValues = z.infer<typeof schema>

export function ForgotPasswordForm() {
  const forgotPassword = useForgotPassword()
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "" },
  })

  function onSubmit(values: FormValues) {
    forgotPassword.mutate(values.email)
  }

  return (
    <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
      <CardHeader>
        <CardTitle className="text-xl">Recuperare parolă</CardTitle>
        <CardDescription>
          Introdu adresa de email și îți vom trimite un link de resetare.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {forgotPassword.isSuccess ? (
          <p className="text-sm text-muted-foreground">
            Dacă adresa există în sistem, vei primi un email cu instrucțiuni de resetare a parolei.
          </p>
        ) : (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" placeholder="nume@companie.ro" autoComplete="email" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" className="w-full" disabled={forgotPassword.isPending}>
                {forgotPassword.isPending ? "Se trimite..." : "Trimite link de resetare"}
              </Button>
            </form>
          </Form>
        )}
        <p className="mt-6 text-center text-sm text-muted-foreground">
          <Link href="/login" className="font-medium text-foreground hover:underline">
            Înapoi la autentificare
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
