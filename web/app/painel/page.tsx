import type { Metadata } from "next";
import Painel from "./Painel";
import "./painel.css";

export const metadata: Metadata = { title: "Painel do psicólogo", robots: { index: false } };

export default function Page() {
  return <Painel />;
}
