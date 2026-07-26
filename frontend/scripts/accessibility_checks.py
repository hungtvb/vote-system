#!/usr/bin/env python3
"""Verify the core Ballot Edition foreground/background contrast pairs."""

from __future__ import annotations


def luminance(hex_color: str) -> float:
    channels = [int(hex_color[index:index + 2], 16) / 255 for index in (1, 3, 5)]

    def linearize(channel: float) -> float:
        return channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4

    red, green, blue = (linearize(channel) for channel in channels)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast(first: str, second: str) -> float:
    bright, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (bright + 0.05) / (dark + 0.05)


PAIRS = {
    "navy on paper": ("#1e2a3a", "#fbf9f4", 4.5),
    "graphite on paper": ("#3a362e", "#fbf9f4", 4.5),
    "muted on paper": ("#6f6a60", "#fbf9f4", 4.5),
    "seal on paper": ("#b8342e", "#fbf9f4", 4.5),
    "bone on navy": ("#f0e9d8", "#1e2a3a", 4.5),
    "kraft on navy": ("#c9a876", "#1e2a3a", 4.5),
}


if __name__ == "__main__":
    failures: list[str] = []
    for label, (foreground, background, minimum) in PAIRS.items():
        ratio = contrast(foreground, background)
        print(f"{label}: {ratio:.2f}:1")
        if ratio < minimum:
            failures.append(f"{label} is {ratio:.2f}:1; expected at least {minimum:.1f}:1")

    if failures:
        raise SystemExit("\n".join(failures))
