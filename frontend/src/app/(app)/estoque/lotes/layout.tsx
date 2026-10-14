import type { ReactNode } from "react";
import { ModuloExigido } from "@/components/modulo-exigido";

export default function Layout({ children }: { children: ReactNode }) {
  return <ModuloExigido modulo="lotes">{children}</ModuloExigido>;
}
