"use client"

import { use } from "react"
import Link from "next/link"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { XCircle } from "lucide-react"
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
import { Skeleton } from "@/components/ui/skeleton"
import { useAcceptInvite, useInviteInfo } from "@/hooks/use-auth"
import { ROLE_LABELS } from "@/lib/roles"

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/

const schema = z.object({
  password: z
    .string()
    .min(8, "Parola trebuie să aibă minim 8 caractere")
    .regex(passwordPattern, "Parola trebuie să conțină o literă mare, una mică și o cifră"),
})

type FormValues = z.infer<typeof schema>

export default function AcceptInvitePage({
  params,
}: {
  params: Promise<{ token: string }>
}) {
  const { token } = use(params)
  const { data: invite, isLoading, isError } = useInviteInfo(token)
  const acceptInvite = useAcceptInvite()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { password: "" },
  })

  function onSubmit(values: FormValues) {
    acceptInvite.mutate({ token, password: values.password })
  }

  if (isLoading) {
    return (
      <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
        <CardContent className="flex flex-col gap-4 pt-6">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </CardContent>
      </Card>
    )
  }

  if (isError || !invite) {
    return (
      <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
        <CardContent className="flex flex-col items-center gap-3 pt-6 text-center">
          <XCircle className="size-10 text-destructive" />
          <h1 className="text-xl font-semibold">Invitație invalidă</h1>
          <p className="text-sm text-muted-foreground">
            Link-ul de invitație este invalid sau a expirat. Cere-i administratorului tău o
            invitație nouă.
          </p>
          <Link href="/login" className="text-sm font-medium text-foreground hover:underline">
            Înapoi la autentificare
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-white/15 bg-background/95 shadow-2xl backdrop-blur-md">
      <CardHeader>
        <CardTitle className="text-xl">Bine ai venit, {invite.firstName}!</CardTitle>
        <CardDescription>
          Ai fost invitat cu rol de <strong>{ROLE_LABELS[invite.role]}</strong>. Alege o parolă
          pentru a-ți activa contul ({invite.email}).
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Parolă</FormLabel>
                  <FormControl>
                    <Input type="password" autoComplete="new-password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" className="w-full" disabled={acceptInvite.isPending}>
              {acceptInvite.isPending ? "Se activează..." : "Activează contul"}
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
