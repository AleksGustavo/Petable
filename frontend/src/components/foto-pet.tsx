import Image from "next/image";
import type { Pet } from "@/lib/pets";

/** Foto servida pelo route handler /api/pets/[id]/foto; sem foto, um avatar com a inicial. */
export function FotoPet({ pet, tamanho = 48 }: { pet: Pick<Pet, "id" | "nome" | "fotoUrl" | "updatedAt">; tamanho?: number }) {
  if (!pet.fotoUrl) {
    return (
      <span
        aria-hidden="true"
        style={{ width: tamanho, height: tamanho, fontSize: tamanho / 2.4 }}
        className="grid shrink-0 place-items-center rounded-full bg-brand-soft font-semibold uppercase text-brand-strong"
      >
        {pet.nome.slice(0, 1)}
      </span>
    );
  }
  return (
    <Image
      src={`/api/pets/${pet.id}/foto?v=${encodeURIComponent(pet.updatedAt)}`}
      alt={`Foto de ${pet.nome}`}
      width={tamanho}
      height={tamanho}
      unoptimized
      className="shrink-0 rounded-full border border-border object-cover"
      style={{ width: tamanho, height: tamanho }}
    />
  );
}
