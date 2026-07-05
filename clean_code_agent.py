#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════╗
║        Clean Code Agent - schoolManagement       ║
║  Analyse Java + TypeScript selon Clean Code      ║
╚══════════════════════════════════════════════════╝
Usage:
  python clean_code_agent.py              # Analyse tout le projet
  python clean_code_agent.py back/        # Analyse le backend uniquement
  python clean_code_agent.py front/src/   # Analyse le frontend uniquement
  python clean_code_agent.py --file path/to/File.java
"""

import os
import re
import sys
import argparse
from dataclasses import dataclass, field
from typing import List, Optional

# ─── ANSI Colors ───────────────────────────────────────────────────────────────
R  = "\033[91m"   # red
Y  = "\033[93m"   # yellow
G  = "\033[92m"   # green
B  = "\033[94m"   # blue
C  = "\033[96m"   # cyan
M  = "\033[95m"   # magenta
W  = "\033[97m"   # white
DIM = "\033[2m"
BOLD = "\033[1m"
RESET = "\033[0m"


# ─── Data classes ──────────────────────────────────────────────────────────────
@dataclass
class Violation:
    severity: str        # ERROR | WARNING | INFO
    rule: str
    message: str
    line: Optional[int] = None

@dataclass
class FileReport:
    path: str
    violations: List[Violation] = field(default_factory=list)
    lines: int = 0

    @property
    def errors(self):   return [v for v in self.violations if v.severity == "ERROR"]
    @property
    def warnings(self): return [v for v in self.violations if v.severity == "WARNING"]
    @property
    def infos(self):    return [v for v in self.violations if v.severity == "INFO"]

    @property
    def score(self) -> int:
        penalty = len(self.errors) * 10 + len(self.warnings) * 3 + len(self.infos) * 1
        return max(0, 100 - penalty)


# ─── Rules ─────────────────────────────────────────────────────────────────────
class CleanCodeChecker:

    # ── Universal rules ────────────────────────────────────────────────────────
    MAX_FILE_LINES       = 300
    MAX_LINE_LENGTH      = 120
    MAX_METHOD_LINES     = 25
    MAX_PARAMS           = 4
    MAX_NESTING          = 4

    def check_file(self, path: str) -> FileReport:
        ext = os.path.splitext(path)[1]
        try:
            with open(path, encoding="utf-8", errors="ignore") as f:
                content = f.read()
            raw_lines = content.splitlines()
        except Exception as e:
            r = FileReport(path=path)
            r.violations.append(Violation("ERROR", "IO", f"Cannot read file: {e}"))
            return r

        report = FileReport(path=path, lines=len(raw_lines))
        lines = raw_lines  # list of strings

        self._check_file_length(report, lines)
        self._check_line_length(report, lines)
        self._check_todo_fixme(report, lines)
        self._check_magic_numbers(report, lines, ext)
        self._check_console_log(report, lines, ext)
        self._check_commented_code(report, lines, ext)
        self._check_empty_catch(report, lines)
        self._check_nesting(report, lines)

        if ext == ".java":
            self._check_java_methods(report, lines)
            self._check_java_naming(report, lines)
            self._check_java_wildcard_import(report, lines)
            self._check_java_field_injection(report, lines)
        elif ext == ".ts":
            self._check_ts_methods(report, lines)
            self._check_ts_any(report, lines)
            self._check_ts_naming(report, lines)
            self._check_ts_missing_return_type(report, lines)

        return report

    # ── File-level checks ──────────────────────────────────────────────────────
    def _check_file_length(self, r, lines):
        if len(lines) > self.MAX_FILE_LINES:
            r.violations.append(Violation(
                "WARNING", "LargeFile",
                f"Fichier trop long: {len(lines)} lignes (max {self.MAX_FILE_LINES}). Pensez à découper."
            ))

    def _check_line_length(self, r, lines):
        for i, line in enumerate(lines, 1):
            stripped = line.rstrip()
            if len(stripped) > self.MAX_LINE_LENGTH:
                r.violations.append(Violation(
                    "INFO", "LongLine",
                    f"Ligne trop longue: {len(stripped)} caractères (max {self.MAX_LINE_LENGTH})",
                    line=i
                ))

    # ── Comment checks ─────────────────────────────────────────────────────────
    def _check_todo_fixme(self, r, lines):
        pattern = re.compile(r'\b(TODO|FIXME|HACK|XXX|BUG)\b', re.IGNORECASE)
        for i, line in enumerate(lines, 1):
            m = pattern.search(line)
            if m:
                tag = m.group(1).upper()
                sev = "ERROR" if tag in ("FIXME", "BUG") else "WARNING"
                r.violations.append(Violation(sev, "TodoComment",
                    f"Tag '{tag}' trouvé — travail non terminé", line=i))

    def _check_commented_code(self, r, lines, ext):
        """Detect blocks of commented-out code (3+ consecutive)."""
        if ext == ".java":
            comment_re = re.compile(r'^\s*//')
        elif ext == ".ts":
            comment_re = re.compile(r'^\s*//')
        else:
            return

        consecutive = 0
        start_line = 0
        for i, line in enumerate(lines, 1):
            if comment_re.match(line) and (';' in line or '{' in line or '}' in line):
                if consecutive == 0:
                    start_line = i
                consecutive += 1
            else:
                if consecutive >= 3:
                    r.violations.append(Violation(
                        "WARNING", "CommentedCode",
                        f"Bloc de code commenté ({consecutive} lignes) — à supprimer ou restaurer",
                        line=start_line
                    ))
                consecutive = 0

    # ── Magic numbers ──────────────────────────────────────────────────────────
    def _check_magic_numbers(self, r, lines, ext):
        if ext not in (".java", ".ts"):
            return
        # Skip lines that are constants definitions or annotations
        magic_re = re.compile(r'(?<!\w)([2-9]\d{1,}|\d{3,})(?!\w)')
        skip_re  = re.compile(r'(static final|const |@|duration|Duration|version|port|PORT)', re.IGNORECASE)
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if skip_re.search(line):
                continue
            m = magic_re.search(line)
            if m:
                r.violations.append(Violation(
                    "INFO", "MagicNumber",
                    f"Nombre magique '{m.group()}' — extraire en constante nommée",
                    line=i
                ))

    # ── Debug statements ───────────────────────────────────────────────────────
    def _check_console_log(self, r, lines, ext):
        if ext != ".ts":
            return
        log_re = re.compile(r'\bconsole\.(log|warn|error|debug)\b')
        for i, line in enumerate(lines, 1):
            if log_re.search(line):
                r.violations.append(Violation(
                    "WARNING", "ConsoleLog",
                    "console.log/warn/error en production — utiliser un service de logging",
                    line=i
                ))

    # ── Empty catch ────────────────────────────────────────────────────────────
    def _check_empty_catch(self, r, lines):
        catch_re = re.compile(r'\bcatch\s*\(')
        for i, line in enumerate(lines, 1):
            if catch_re.search(line):
                # Check if next non-blank line is just closing brace
                body_lines = []
                for j in range(i, min(i + 5, len(lines))):
                    body_lines.append(lines[j].strip())
                body = " ".join(body_lines)
                if re.search(r'catch\s*\([^)]*\)\s*\{\s*\}', body):
                    r.violations.append(Violation(
                        "ERROR", "EmptyCatch",
                        "Bloc catch vide — au minimum logger l'exception",
                        line=i
                    ))

    # ── Nesting depth ──────────────────────────────────────────────────────────
    def _check_nesting(self, r, lines):
        for i, line in enumerate(lines, 1):
            stripped = line.rstrip()
            # Count leading 4-space indents
            indent = len(stripped) - len(stripped.lstrip())
            level = indent // 4
            if level > self.MAX_NESTING:
                r.violations.append(Violation(
                    "WARNING", "DeepNesting",
                    f"Imbrication trop profonde (niveau {level}) — extraire en méthode",
                    line=i
                ))

    # ── Java-specific ──────────────────────────────────────────────────────────
    def _check_java_methods(self, r, lines):
        method_re = re.compile(
            r'^\s+(public|private|protected|default)\s+[\w<>\[\],\s]+\s+\w+\s*\('
        )
        in_method = False
        method_start = 0
        method_name = ""
        brace_count = 0

        for i, line in enumerate(lines, 1):
            if not in_method:
                m = method_re.match(line)
                if m and '{' in line:
                    in_method = True
                    method_start = i
                    brace_count = line.count('{') - line.count('}')
                    nm = re.search(r'\s(\w+)\s*\(', line)
                    method_name = nm.group(1) if nm else "?"
                elif m:
                    # method signature without opening brace yet
                    nm = re.search(r'\s(\w+)\s*\(', line)
                    method_name = nm.group(1) if nm else "?"
                    method_start = i
                    brace_count = 0
                    in_method = True
            else:
                brace_count += line.count('{') - line.count('}')
                if brace_count <= 0:
                    length = i - method_start
                    if length > self.MAX_METHOD_LINES:
                        r.violations.append(Violation(
                            "WARNING", "LongMethod",
                            f"Méthode '{method_name}' trop longue: {length} lignes (max {self.MAX_METHOD_LINES})",
                            line=method_start
                        ))
                    in_method = False

        # Check constructor/method parameters
        param_re = re.compile(r'\(([^)]{80,})\)')
        for i, line in enumerate(lines, 1):
            m = param_re.search(line)
            if m:
                params = [p.strip() for p in m.group(1).split(',') if p.strip()]
                if len(params) > self.MAX_PARAMS:
                    nm = re.search(r'\s(\w+)\s*\(', line)
                    name = nm.group(1) if nm else "?"
                    r.violations.append(Violation(
                        "WARNING", "TooManyParams",
                        f"'{name}' a {len(params)} paramètres (max {self.MAX_PARAMS}) — utiliser un objet",
                        line=i
                    ))

    def _check_java_naming(self, r, lines):
        # Class names must be PascalCase
        class_re  = re.compile(r'^\s*(public|private|protected)?\s*(class|interface|enum)\s+([a-z]\w*)')
        # Constants should be UPPER_SNAKE
        const_re  = re.compile(r'static\s+final\s+\w+\s+([a-z][a-zA-Z]+)\b')
        for i, line in enumerate(lines, 1):
            m = class_re.search(line)
            if m:
                r.violations.append(Violation(
                    "ERROR", "NamingConvention",
                    f"Classe/Interface '{m.group(3)}' doit commencer par une majuscule (PascalCase)",
                    line=i
                ))
            m2 = const_re.search(line)
            if m2:
                r.violations.append(Violation(
                    "INFO", "NamingConvention",
                    f"Constante '{m2.group(1)}' devrait être en UPPER_SNAKE_CASE",
                    line=i
                ))

    def _check_java_wildcard_import(self, r, lines):
        for i, line in enumerate(lines, 1):
            if re.search(r'^import\s+[\w.]+\.\*;', line.strip()):
                r.violations.append(Violation(
                    "WARNING", "WildcardImport",
                    "Import générique (.*) — préférer les imports explicites",
                    line=i
                ))

    def _check_java_field_injection(self, r, lines):
        for i, line in enumerate(lines, 1):
            if re.search(r'@Autowired', line) and i < len(lines):
                # Check if next non-blank line has a field (not constructor/method)
                next_lines = " ".join(lines[i:i+3])
                if re.search(r'private\s+\w+\s+\w+\s*;', next_lines):
                    r.violations.append(Violation(
                        "WARNING", "FieldInjection",
                        "@Autowired sur un champ — préférer l'injection par constructeur",
                        line=i
                    ))

    # ── TypeScript-specific ────────────────────────────────────────────────────
    def _check_ts_methods(self, r, lines):
        method_re = re.compile(r'^\s+\w[\w$]*\s*\([^)]*\)\s*(?::\s*\w[\w<>\[\]|,\s]*)?\s*\{')
        in_method = False
        method_start = 0
        method_name = ""
        brace_count = 0

        for i, line in enumerate(lines, 1):
            if not in_method:
                m = method_re.match(line)
                if m:
                    in_method = True
                    method_start = i
                    brace_count = line.count('{') - line.count('}')
                    nm = re.search(r'(\w[\w$]*)\s*\(', line)
                    method_name = nm.group(1) if nm else "?"
            else:
                brace_count += line.count('{') - line.count('}')
                if brace_count <= 0:
                    length = i - method_start
                    if length > self.MAX_METHOD_LINES:
                        r.violations.append(Violation(
                            "WARNING", "LongMethod",
                            f"Méthode '{method_name}' trop longue: {length} lignes (max {self.MAX_METHOD_LINES})",
                            line=method_start
                        ))
                    in_method = False

    def _check_ts_any(self, r, lines):
        any_re = re.compile(r':\s*any\b|as\s+any\b|<any>')
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if any_re.search(line):
                r.violations.append(Violation(
                    "WARNING", "AnyType",
                    "Utilisation de 'any' — typer précisément pour bénéficier de TypeScript",
                    line=i
                ))

    def _check_ts_naming(self, r, lines):
        # Component/Service/etc class names
        class_re = re.compile(r'^export\s+class\s+([a-z]\w*)')
        for i, line in enumerate(lines, 1):
            m = class_re.search(line.strip())
            if m:
                r.violations.append(Violation(
                    "ERROR", "NamingConvention",
                    f"Classe '{m.group(1)}' doit commencer par une majuscule (PascalCase)",
                    line=i
                ))

    def _check_ts_missing_return_type(self, r, lines):
        # Public methods without return type
        method_re = re.compile(r'^\s+(public\s+)?(?!constructor|ngOn|ngAfter|ngDo)(\w+)\s*\([^)]*\)\s*\{')
        for i, line in enumerate(lines, 1):
            m = method_re.match(line)
            if m and ':' not in line.split('(')[0]:
                # likely missing return type
                method_nm = m.group(2)
                if method_nm not in ('constructor', 'ngOnInit', 'ngOnDestroy', 'ngAfterViewInit'):
                    r.violations.append(Violation(
                        "INFO", "MissingReturnType",
                        f"Méthode '{method_nm}' sans type de retour explicite",
                        line=i
                    ))


# ─── Scanner ───────────────────────────────────────────────────────────────────
IGNORED_DIRS = {
    'node_modules', '.git', 'target', 'dist', '.angular',
    '__pycache__', '.idea', '.vscode', 'uploads'
}
SUPPORTED_EXTS = {'.java', '.ts'}


def collect_files(root: str) -> List[str]:
    result = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in IGNORED_DIRS]
        for fn in filenames:
            ext = os.path.splitext(fn)[1]
            if ext in SUPPORTED_EXTS:
                # Skip spec/test files option
                if fn.endswith('.spec.ts') or 'Test' in fn:
                    continue
                result.append(os.path.join(dirpath, fn))
    return sorted(result)


# ─── Reporter ──────────────────────────────────────────────────────────────────
SEV_COLOR = {"ERROR": R, "WARNING": Y, "INFO": C}
SEV_ICON  = {"ERROR": "✖", "WARNING": "⚠", "INFO": "ℹ"}


def severity_badge(sev: str) -> str:
    color = SEV_COLOR.get(sev, W)
    icon  = SEV_ICON.get(sev, "•")
    return f"{color}{BOLD}{icon} {sev}{RESET}"


def score_color(score: int) -> str:
    if score >= 80: return G
    if score >= 60: return Y
    return R


def score_bar(score: int, width: int = 30) -> str:
    filled = int(width * score / 100)
    bar = "█" * filled + "░" * (width - filled)
    color = score_color(score)
    return f"{color}{bar}{RESET} {color}{BOLD}{score}/100{RESET}"


def letter_grade(score: int) -> str:
    if score >= 90: return f"{G}{BOLD}A{RESET}"
    if score >= 80: return f"{G}B{RESET}"
    if score >= 70: return f"{Y}C{RESET}"
    if score >= 60: return f"{Y}D{RESET}"
    return f"{R}{BOLD}F{RESET}"


def print_report(reports: List[FileReport], verbose: bool = True):
    print(f"\n{BOLD}{C}{'═'*60}{RESET}")
    print(f"{BOLD}{C}  🔍 Clean Code Agent — schoolManagement{RESET}")
    print(f"{BOLD}{C}{'═'*60}{RESET}\n")

    total_errors   = 0
    total_warnings = 0
    total_infos    = 0
    files_clean    = 0
    scores         = []

    for rep in reports:
        if not rep.violations:
            files_clean += 1
            if verbose:
                rel = os.path.relpath(rep.path)
                print(f"  {G}✔{RESET} {DIM}{rel}{RESET} {G}— Clean ✓{RESET}")
            continue

        total_errors   += len(rep.errors)
        total_warnings += len(rep.warnings)
        total_infos    += len(rep.infos)
        scores.append(rep.score)

        rel = os.path.relpath(rep.path)
        ext = os.path.splitext(rep.path)[1]
        lang_icon = "☕" if ext == ".java" else "🅰"

        print(f"\n  {lang_icon} {BOLD}{W}{rel}{RESET}  {score_bar(rep.score, 20)}")
        print(f"  {DIM}{rep.lines} lignes  •  {len(rep.errors)} erreurs  •  {len(rep.warnings)} avertissements  •  {len(rep.infos)} infos{RESET}")

        # Group violations by rule
        by_rule: dict[str, List[Violation]] = {}
        for v in rep.violations:
            by_rule.setdefault(v.rule, []).append(v)

        for rule, viols in by_rule.items():
            # Show first 3, summarize rest
            for v in viols[:3]:
                loc = f"{DIM}L{v.line}{RESET}  " if v.line else "      "
                print(f"    {loc}{severity_badge(v.severity)}  {DIM}[{rule}]{RESET}  {v.message}")
            if len(viols) > 3:
                print(f"    {DIM}    … +{len(viols)-3} occurrence(s) similaire(s){RESET}")

    # ── Summary ────────────────────────────────────────────────────────────────
    total_files = len(reports)
    avg_score   = int(sum(scores) / len(scores)) if scores else 100
    worst = sorted(reports, key=lambda r: r.score)[:3]

    print(f"\n{BOLD}{C}{'═'*60}{RESET}")
    print(f"{BOLD}  📊 RÉSUMÉ{RESET}")
    print(f"{BOLD}{C}{'═'*60}{RESET}")
    print(f"  Fichiers analysés : {W}{BOLD}{total_files}{RESET}")
    print(f"  Fichiers propres  : {G}{BOLD}{files_clean}{RESET}")
    print(f"  Erreurs           : {R}{BOLD}{total_errors}{RESET}")
    print(f"  Avertissements    : {Y}{BOLD}{total_warnings}{RESET}")
    print(f"  Infos             : {C}{BOLD}{total_infos}{RESET}")
    print(f"\n  Score moyen       : {score_bar(avg_score)}")
    print(f"  Note globale      : {letter_grade(avg_score)}")

    if worst and any(r.violations for r in worst):
        print(f"\n  {Y}{BOLD}⚑ Fichiers les plus critiques :{RESET}")
        for rep in worst:
            if rep.violations:
                rel = os.path.relpath(rep.path)
                print(f"    {R}•{RESET} {rel}  {score_color(rep.score)}{BOLD}{rep.score}/100{RESET}")

    print(f"\n{BOLD}{C}{'═'*60}{RESET}\n")


# ─── Main ──────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="Clean Code Agent — analyse Java & TypeScript"
    )
    parser.add_argument(
        "paths", nargs="*",
        default=["."],
        help="Répertoires ou fichiers à analyser (défaut: répertoire courant)"
    )
    parser.add_argument(
        "--file", "-f", metavar="FILE",
        help="Analyser un seul fichier"
    )
    parser.add_argument(
        "--quiet", "-q", action="store_true",
        help="Afficher uniquement les fichiers avec violations"
    )
    parser.add_argument(
        "--min-score", "-s", type=int, default=0,
        help="Code de sortie non-zéro si score moyen < valeur (ex: --min-score 70)"
    )
    args = parser.parse_args()

    checker = CleanCodeChecker()
    reports: List[FileReport] = []

    if args.file:
        reports.append(checker.check_file(args.file))
    else:
        files: List[str] = []
        for p in args.paths:
            if os.path.isfile(p):
                files.append(p)
            elif os.path.isdir(p):
                files.extend(collect_files(p))
            else:
                print(f"{R}Chemin introuvable: {p}{RESET}")
        if not files:
            print(f"{Y}Aucun fichier .java/.ts trouvé.{RESET}")
            sys.exit(0)
        print(f"{DIM}Analyse de {len(files)} fichier(s)...{RESET}")
        for fp in files:
            reports.append(checker.check_file(fp))

    verbose = not args.quiet
    print_report(reports, verbose=verbose)

    # Exit code for CI
    scores = [r.score for r in reports if r.violations]
    avg = int(sum(scores) / len(scores)) if scores else 100
    if args.min_score and avg < args.min_score:
        sys.exit(1)


if __name__ == "__main__":
    main()
