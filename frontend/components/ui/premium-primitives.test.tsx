import { Building2, Wallet } from "lucide-react"
import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import { EmptyState } from "@/components/ui/empty-state"
import { PageHeader, SectionHeader } from "@/components/ui/page-header"
import { StatCard } from "@/components/ui/stat-card"
import { StatusBadge } from "@/components/ui/status-badge"

describe("premium UI primitives", () => {
  it("keeps page and section heading levels semantic", () => {
    render(<><PageHeader eyebrow="Administrare" title="Proprietăți" description="Portofoliul curent" /><SectionHeader title="Activitate recentă" /></>)
    expect(screen.getByRole("heading", { level: 1, name: "Proprietăți" })).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 2, name: "Activitate recentă" })).toBeInTheDocument()
    expect(screen.getByText("Portofoliul curent")).toBeInTheDocument()
  })

  it("renders an empty state with a named action", () => {
    render(<EmptyState icon={Building2} title="Nu sunt proprietăți" description="Revino mai târziu." action={<button>Reîncearcă</button>} />)
    expect(screen.getByRole("heading", { name: "Nu sunt proprietăți" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Reîncearcă" })).toBeInTheDocument()
  })

  it("exposes status and KPI meaning as text, not color alone", () => {
    render(<><StatusBadge tone="success" label="Activ" /><StatCard icon={Wallet} label="Venit total" value="1.200 RON" /></>)
    expect(screen.getByText("Activ")).toBeInTheDocument()
    expect(screen.getByText("Venit total")).toBeInTheDocument()
    expect(screen.getByText("1.200 RON")).toBeInTheDocument()
  })
})
