#!/usr/bin/env python3
"""Render the naive-vs-Lua consistency figure from a probe result.

    python benchmark/concurrency/render-lua-consistency-chart.py \
        benchmark/concurrency/results/lua-vs-naive-20261014.json \
        docs/assets/readme/lua-consistency.png

The numbers come from ``lua-vs-naive-consistency.py``; this script only draws them,
so a chart that disagrees with the JSON is a bug in one file rather than a story
about the other. Requires matplotlib.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402  (backend must be set before pyplot)

BG = "#0d1117"
CARD = "#161b22"
FG = "#e6edf3"
MUTED = "#8b949e"
GRID = "#21262d"
EMERALD = "#10b981"
CYAN = "#06b6d4"
PURPLE = "#a855f7"
RED = "#f85149"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = json.loads(args.result.read_text(encoding="utf-8"))
    lua = data["paths"]["lua"]
    naive = data["paths"]["naive"]
    threads = data["threads"]
    rounds = data["rounds"]
    weight = data["weight"]

    fig, (ax_bar, ax_strip) = plt.subplots(
        1, 2, figsize=(13.5, 5.6), dpi=160, gridspec_kw={"width_ratios": [1.0, 1.25]}
    )
    fig.patch.set_facecolor(BG)
    for ax in (ax_bar, ax_strip):
        ax.set_facecolor(CARD)
        for spine in ax.spines.values():
            spine.set_color(GRID)
        ax.tick_params(colors=MUTED)

    # ---- Panel A: did the two structures agree? -------------------------------
    paths = ["lua", "naive"]
    labels = ["Lua atomic script", "Naive round trips"]
    consistent = [data["paths"][p]["sameUserBurst"]["consistentRounds"] for p in paths]
    drifted = [data["paths"][p]["sameUserBurst"]["driftedRounds"] for p in paths]
    y = [1, 0]

    ax_bar.barh(y, consistent, height=0.55, color=EMERALD, label="agreed")
    ax_bar.barh(y, drifted, height=0.55, left=consistent, color=RED, label="drifted")
    for index, path_label in enumerate(labels):
        row = y[index]
        if consistent[index] >= 2:
            ax_bar.text(
                consistent[index] / 2,
                row,
                f"{consistent[index]}/{rounds}",
                ha="center",
                va="center",
                color="#04231a",
                fontsize=11,
                fontweight="bold",
            )
        elif consistent[index]:
            ax_bar.text(
                consistent[index] + 0.3,
                row + 0.42,
                f"{consistent[index]}/{rounds} agreed",
                ha="left",
                va="bottom",
                color=EMERALD,
                fontsize=9,
                fontweight="bold",
            )
        if drifted[index]:
            ax_bar.text(
                consistent[index] + drifted[index] / 2,
                row,
                f"{drifted[index]}/{rounds}",
                ha="center",
                va="center",
                color="#ffffff",
                fontsize=11,
                fontweight="bold",
            )
    ax_bar.set_yticks(y, labels, color=FG, fontsize=11)
    ax_bar.set_ylim(-0.7, 1.7)
    ax_bar.set_xlim(0, rounds)
    ax_bar.set_xlabel(f"rounds out of {rounds}", color=MUTED, fontsize=10)
    ax_bar.set_title(
        f"Same user, {threads} concurrent toggles\nzscore vs scard must agree every round",
        color=FG,
        fontsize=12,
        fontweight="bold",
        loc="left",
        pad=12,
    )
    ax_bar.legend(
        facecolor=CARD,
        edgecolor=GRID,
        labelcolor=FG,
        loc="lower center",
        bbox_to_anchor=(0.5, -0.32),
        ncol=2,
        fontsize=9,
    )
    ax_bar.xaxis.grid(True, color=GRID, linewidth=0.8)
    ax_bar.set_axisbelow(True)

    # ---- Panel B: how far apart did they end up? ------------------------------
    naive_drift = [entry["drift"] for entry in naive["sameUserBurst"]["samples"]]
    lua_drift = [entry["drift"] for entry in lua["sameUserBurst"]["samples"]]
    rounds_axis = list(range(1, rounds + 1))

    ax_strip.axhline(0, color=CYAN, linewidth=1.2, linestyle="--", zorder=1)
    ax_strip.scatter(
        rounds_axis,
        naive_drift,
        s=46,
        color=RED,
        edgecolors="#ffb4ab",
        linewidths=0.6,
        zorder=3,
        label="Naive round trips",
    )
    ax_strip.scatter(
        rounds_axis,
        lua_drift,
        s=46,
        color=EMERALD,
        edgecolors="#a7f3d0",
        linewidths=0.6,
        zorder=4,
        label="Lua atomic script",
    )
    worst = max((abs(value) for value in naive_drift), default=0)
    ax_strip.annotate(
        f"worst gap: {worst}",
        xy=(naive_drift.index(max(naive_drift, key=abs)) + 1, max(naive_drift, key=abs)),
        xytext=(6, 10),
        textcoords="offset points",
        color=RED,
        fontsize=9,
    )
    ax_strip.set_xlim(0.4, rounds + 0.6)
    ax_strip.set_xlabel("round", color=MUTED, fontsize=10)
    ax_strip.set_ylabel(f"zscore - scard x {weight}", color=MUTED, fontsize=10)
    ax_strip.set_title(
        "Final ranking drift, per round\n0 means the like-set and the hot-ranking ZSET agree",
        color=FG,
        fontsize=12,
        fontweight="bold",
        loc="left",
        pad=12,
    )
    ax_strip.legend(facecolor=CARD, edgecolor=GRID, labelcolor=FG, loc="upper left", fontsize=9)
    ax_strip.yaxis.grid(True, color=GRID, linewidth=0.8)
    ax_strip.set_axisbelow(True)

    fig.suptitle(
        "Redis like-toggle: Lua atomicity vs the same steps as separate commands",
        color=FG,
        fontsize=15,
        fontweight="bold",
        x=0.012,
        ha="left",
    )
    fig.text(
        0.012,
        0.015,
        f"redis-server {data['redisVersion']}  |  {threads} threads, {rounds} rounds, weight {weight}  |  "
        "benchmark/concurrency/lua-vs-naive-consistency.py",
        color=MUTED,
        fontsize=8.5,
        ha="left",
    )
    fig.tight_layout(rect=(0, 0.035, 1, 0.93))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.output, facecolor=BG)
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
