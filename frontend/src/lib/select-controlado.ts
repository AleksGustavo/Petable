import { useLayoutEffect, useRef } from "react";

/**
 * Ref para um <select> controlado dentro de um form com Server Action.
 *
 * Depois da action, o React 19 reseta o form e o <select> volta à primeira opção
 * sem avisar o React, que segue achando que o valor é o do estado (o `key` não
 * resolve aqui: um select controlado não marca a opção como `defaultSelected`).
 * A cada nova resposta da action, reaplica no DOM o valor do estado.
 */
export function useSelectControlado(valor: string, resposta: unknown) {
  const ref = useRef<HTMLSelectElement>(null);
  useLayoutEffect(() => {
    if (ref.current && ref.current.value !== valor) ref.current.value = valor;
  }, [valor, resposta]);
  return ref;
}
