# Documentation Maintenance Rule

Whenever modifications, additions, or removals are made to MinionX features, you MUST immediately update `WIKI.md` in the project root to reflect those changes.

## Guiding Principles for WIKI.md:
1. **Target Audience Hierarchy**:
   - **Primary Audience**: Server Owners and Administrators (non-coders). Documentation must be user-friendly, clear, step-by-step, with practical in-game command examples, GUI diagrams, and configuration guidance.
   - **Secondary Audience**: Plugin Developers and Technical Staff. Clear API reference, event listeners, registry methods, and dependency snippets at the end of the document.

2. **Mandatory Documentation Trigger**:
   - Any new command or subcommand -> Update "Commands & Permissions" section.
   - Any new permission node -> Update "Permissions Reference" table.
   - Any new configuration setting -> Update "Configuration Guide (`config.yml`)" section.
   - Any new minion archetype, fuel, upgrade, or storage -> Update the relevant game mechanic section.
   - Any API method or event addition -> Update the "Developer API Reference" section.
   - Any bug fix or behavior change -> Update the "Changelog & Version History" table in `WIKI.md`.

3. **Format & Tone**:
   - Use clean GitHub-flavored markdown with visual tables, alerts (`> [!TIP]`, `> [!IMPORTANT]`), and command syntax blocks.
   - Brand integrity: Always refer to the plugin as **MinionX** by **AbhinavVanama**. Never use outdated names (e.g. Hypixel or SkyBlock).
