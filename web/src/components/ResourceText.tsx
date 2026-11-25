import { Fragment } from "react";

export interface ResourceLink { text: string; url: string }

/** Adds real destinations to the supplied prose without changing its wording. */
export function ResourceText({ text, links }: { text: string; links: readonly ResourceLink[] }) {
  if (!links.length) return <>{text}</>;
  const escaped = [...links].sort((a, b) => b.text.length - a.text.length).map(link => link.text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const parts = text.split(new RegExp(`(${escaped.join("|")})`, "g"));
  return <>{parts.map((part, index) => {
    const link = links.find(item => item.text === part);
    return link ? <a key={index} href={link.url} className="text-link">{part}</a> : <Fragment key={index}>{part}</Fragment>;
  })}</>;
}
