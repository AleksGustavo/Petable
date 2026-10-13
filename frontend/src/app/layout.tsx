import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { headers } from "next/headers";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

export const metadata: Metadata = {
  title: { default: "PetConnect Web", template: "%s · PetConnect Web" },
  description: "Gestão de petshop: clientes, pets, serviços, PDV e estoque.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f7fbfb" },
    { media: "(prefers-color-scheme: dark)", color: "#1b2126" },
  ],
};

/**
 * Ler os cabeçalhos torna toda página dinâmica, e o Next aplica o nonce da CSP (proxy.ts): uma página
 * pré-renderizada no build teria scripts sem nonce e seria bloqueada pela CSP.
 */
export default async function RootLayout({ children }: LayoutProps<"/">) {
  await headers();
  return (
    <html lang="pt-BR" className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}>
      <body className="min-h-full font-sans">{children}</body>
    </html>
  );
}
