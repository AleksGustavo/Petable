import type { InputHTMLAttributes } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  name: string;
  rotulo: string;
  erro?: string;
  ajuda?: string;
};

/** Input com rótulo e mensagem de erro ligados por aria-describedby. */
export function Campo({ rotulo, erro, ajuda, name, className = "", ...rest }: Props) {
  const id = rest.id ?? `campo-${name}`;
  const descId = erro ? `${id}-erro` : ajuda ? `${id}-ajuda` : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {rotulo}
        {rest.required && <span className="text-danger"> *</span>}
      </label>
      <input id={id} name={name} aria-invalid={erro ? true : undefined} aria-describedby={descId} className="campo" {...rest} />
      {erro ? (
        <p id={descId} className="mt-1 text-xs text-danger">
          {erro}
        </p>
      ) : ajuda ? (
        <p id={descId} className="mt-1 text-xs text-muted">
          {ajuda}
        </p>
      ) : null}
    </div>
  );
}
