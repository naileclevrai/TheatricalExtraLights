"""Generate grandMA2 fixture XMLs for the Theatrical Extra Lights RGB Bar.

Two modes, matching the block's DMX personalities:
- 4CH:  Dim, R, G, B for the whole bar (single instance).
- 36CH: Dim/R/G/B per pixel, 9 pixels, no master dimmer. Built as a multi-instance type
  (a channel-less MAIN + 9 x PIXEL) so the desk shows the pixels as sub-fixtures (1.1 .. 1.9)
  and the effect engine, the colour picker and pixel layouts work per pixel, dimmer chases
  included. Selecting the main fixture addresses all nine pixels, so the fixture dimmer still
  fades the whole bar.

Channel conventions come from gen_ma2_gobo.py in this folder.
"""
import sys
from xml.sax.saxutils import escape

from gen_ma2_gobo import dimmer, rgb

PIXELS = 9
NAME = "THEATRICAL RGB BAR"

HEADER = '''<?xml version="1.0" encoding="UTF-8"?>
<MA xmlns:xml="http://www.w3.org/XML/1998/namespace" major_vers="3" minor_vers="2" stream_vers="2">
  <Info datetime="2026-9-26T22:00:00" showfile="ma fixture builder"/>'''

FOOTER = '''    <Wheels index="2"/>
    <VirtualFunctionBlocks index="3"/>
    <FixtureMacroCollect index="5"/>
  </FixtureType>
</MA>
'''


def rgb_channels(idx0, ch0):
    return [
        rgb(idx0 + 0, ch0 + 0, 1, "Red", "ff0000"),
        rgb(idx0 + 1, ch0 + 1, 2, "Green", "00ff00"),
        rgb(idx0 + 2, ch0 + 2, 3, "Blue", "0000ff"),
    ]


def module(index, name, size_x, channels):
    body = ("\n" + "\n".join(channels)) if channels else ""
    return f'''      <Module index="{index}" name="{name}" class="LED" beamtype="Wash" beam_angle="28" beam_intensity="3000">
        <Body>
          <Size x="{size_x}" y="0.08" z="0.08"/>
        </Body>{body}
      </Module>'''


def fixture_4():
    channels = [dimmer(0, 1)] + rgb_channels(1, 2)
    info = ("Theatrical Extra Lights RGB Bar in the 4-channel personality: Dim, Red, Green, Blue for the "
            "whole bar. Requires mod personality \"4-Channel Mode\".")
    return f'''{HEADER}
  <FixtureType index="0" name="{NAME}" mode="4CH">
    <InfoItems>
      <Info>{escape(info)}</Info>
    </InfoItems>
    <short_name>TEL-Bar</short_name>
    <manufacturer>Nailec</manufacturer>
    <short_manufacturer>Nailec</short_manufacturer>
    <Modules index="0">
{module(0, "MAIN", "1.0", channels)}
    </Modules>
    <Instances index="1">
      <Instance index="0" patch="1" module_index="0" name="Main"/>
    </Instances>
{FOOTER}'''


def fixture_pixel():
    # Module 0 = the bar itself, no channels of its own; module 1 = one pixel (Dim/R/G/B, 4 channels,
    # patched 9 times from channel 1).
    stride = 4
    total = PIXELS * stride
    main = module(0, "MAIN", "1.0", [])
    pixel = module(1, "PIXEL", str(round(1.0 / PIXELS, 3)), [dimmer(0, 1)] + rgb_channels(1, 2))
    instances = ['      <Instance index="0" patch="1" module_index="0" name="Main"/>']
    for p in range(PIXELS):
        instances.append(f'      <Instance index="{p + 1}" patch="{1 + p * stride}" module_index="1" name="Pixel {p + 1}"/>')
    info = (f"Theatrical Extra Lights RGB Bar in the {total}-channel pixel personality: Dimmer/Red/Green/Blue for each "
            f"of the {PIXELS} pixels, left to right, no master dimmer. The pixels are sub-fixtures 1.1 to 1.{PIXELS}, each "
            f"with its own dimmer and colour; the main fixture has no channels, selecting it addresses all nine pixels. "
            f"Requires mod personality \"{total}-Channel Pixel Mode ({PIXELS}x Dim/RGB)\".")
    return f'''{HEADER}
  <FixtureType index="0" name="{NAME}" mode="{total}CH Pixel">
    <InfoItems>
      <Info>{escape(info)}</Info>
    </InfoItems>
    <short_name>TEL-BarPx</short_name>
    <manufacturer>Nailec</manufacturer>
    <short_manufacturer>Nailec</short_manufacturer>
    <Modules index="0">
{main}
{pixel}
    </Modules>
    <Instances index="1">
{chr(10).join(instances)}
    </Instances>
{FOOTER}'''


if __name__ == "__main__":
    out_dir = sys.argv[1]
    with open(f"{out_dir}/nailec@theatrical_rgb_bar@4ch.xml", "w", encoding="utf-8") as f:
        f.write(fixture_4())
    with open(f"{out_dir}/nailec@theatrical_rgb_bar@36ch_pixel.xml", "w", encoding="utf-8") as f:
        f.write(fixture_pixel())
    print("ok")
