import { corsHeaders, json } from "../_shared/auth.ts";

const FIELD_MAP: Record<string, Record<string, [string, string]>> = {
  courses: {
    title: ["title_ar", "title_en"],
    subtitle: ["subtitle_ar", "subtitle_en"],
    description: ["description_ar", "description_en"],
  },
  lessons: {
    title: ["title_ar", "title_en"],
    description: ["description_ar", "description_en"],
    content: ["content_ar", "content_en"],
  },
  course_sections: {
    title: ["title_ar", "title_en"],
    subtitle: ["subtitle_ar", "subtitle_en"],
  },
  categories: {
    name: ["name_ar", "name_en"],
  },
  cms_banners: {
    title: ["title_ar", "title_en"],
    subtitle: ["subtitle_ar", "subtitle_en"],
    cta_label: ["cta_label_ar", "cta_label_en"],
  },
  home_shortcuts: {
    title: ["title_ar", "title_en"],
  },
  exercises: {
    title: ["title_ar", "title_en"],
    description: ["description_ar", "description_en"],
  },
  exercise_questions: {
    question_text: ["question_text_ar", "question_text_en"],
    correct_answer: ["correct_answer_ar", "correct_answer_en"],
  },
  placement_tests: {
    title: ["title_ar", "title_en"],
    description: ["description_ar", "description_en"],
  },
  test_questions: {
    prompt: ["prompt_ar", "prompt_en"],
    explanation: ["explanation_ar", "explanation_en"],
  },
};

function detectLang(text: string): "ar" | "en" {
  const arabicChars = (text.match(/[\u0600-\u06FF]/g) || []).length;
  return arabicChars > text.length * 0.1 ? "ar" : "en";
}

async function translateChunk(text: string, from: "ar" | "en", to: "ar" | "en"): Promise<string> {
  const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${from}|${to}`;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(10_000) });
    if (!res.ok) return text;
    const data = await res.json();
    // MyMemory reports failures (quota, length) with a non-200 responseStatus and an error message as the text.
    if (String(data?.responseStatus) !== "200") return text;
    const translated = data?.responseData?.translatedText;
    if (!translated || translated.toLowerCase() === text.toLowerCase()) return text;
    return translated;
  } catch {
    return text;
  }
}

// MyMemory rejects queries over ~500 chars, so long text is split on sentence/line boundaries.
async function translateText(text: string, from: "ar" | "en"): Promise<string> {
  if (!text || text.trim().length === 0) return text;
  const to = from === "ar" ? "en" : "ar";
  if (text.length <= 450) return translateChunk(text, from, to);
  const parts = text.match(/[^.!?؟\n]+[.!?؟\n]*/g) ?? [text];
  const chunks: string[] = [];
  let cur = "";
  for (const part of parts) {
    if ((cur + part).length > 450 && cur) { chunks.push(cur); cur = part; } else { cur += part; }
  }
  if (cur) chunks.push(cur);
  const out: string[] = [];
  for (const c of chunks) out.push(c.trim() ? await translateChunk(c, from, to) : c);
  return out.join(" ");
}

async function translateOptions(options: unknown, from: "ar" | "en"): Promise<unknown> {
  if (!Array.isArray(options)) return options;
  return Promise.all(options.map((o) => (typeof o === "string" ? translateText(o, from) : o)));
}

async function translateArray(arr: unknown, from: "ar" | "en"): Promise<unknown> {
  if (!Array.isArray(arr)) return arr;
  return Promise.all(arr.map((a) => (typeof a === "string" ? translateText(a, from) : a)));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    // Create admin client directly
    const { createClient } = await import("https://esm.sh/@supabase/supabase-js@2");
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { persistSession: false } }
    );

    const body = await req.json();
    const { table, id, source_lang } = body;
    if (!table || !id) return json({ error: "table and id are required" }, 400);

    const fieldMap = FIELD_MAP[table];
    if (!fieldMap) return json({ error: `Unknown table: ${table}` }, 400);

    const sourceCols = Object.keys(fieldMap);
    const { data: row, error: fetchErr } = await supabase.from(table).select(sourceCols.join(", ")).eq("id", id).single();
    if (fetchErr || !row) return json({ error: "Row not found" }, 404);

    let srcLang: "ar" | "en" = source_lang === "ar" || source_lang === "en" ? source_lang : "en";
    for (const col of sourceCols) {
      const val = row[col];
      if (typeof val === "string" && val.trim().length > 2) {
        srcLang = detectLang(val);
        break;
      }
    }

    const updates: Record<string, unknown> = {};
    const toLang = srcLang === "ar" ? "en" : "ar";

    for (const [srcCol, [arCol, enCol]] of Object.entries(fieldMap)) {
      const sourceVal = row[srcCol];
      if (sourceVal == null || (typeof sourceVal === "string" && sourceVal.trim().length === 0)) continue;

      const isOptions = srcCol.includes("options") && Array.isArray(sourceVal);
      const isTextArray = srcCol.includes("correct_text") && Array.isArray(sourceVal);

      if (isOptions) {
        const translated = await translateOptions(sourceVal, srcLang);
        if (toLang === "ar") { updates[enCol] = sourceVal; updates[arCol] = translated; }
        else { updates[arCol] = sourceVal; updates[enCol] = translated; }
      } else if (isTextArray) {
        const translated = await translateArray(sourceVal, srcLang);
        if (toLang === "ar") { updates[enCol] = sourceVal; updates[arCol] = translated; }
        else { updates[arCol] = sourceVal; updates[enCol] = translated; }
      } else {
        const translated = await translateText(String(sourceVal), srcLang);
        if (toLang === "ar") { updates[enCol] = sourceVal; updates[arCol] = translated; }
        else { updates[arCol] = sourceVal; updates[enCol] = translated; }
      }
    }

    if (Object.keys(updates).length > 0) {
      const { error: updateErr } = await supabase.from(table).update(updates).eq("id", id);
      if (updateErr) return json({ error: updateErr.message }, 500);
    }

    return json({ ok: true, source_lang: srcLang, target_lang: toLang, translated_fields: Object.keys(updates).length });
  } catch (e) {
    return json({ error: e.message }, 500);
  }
});
