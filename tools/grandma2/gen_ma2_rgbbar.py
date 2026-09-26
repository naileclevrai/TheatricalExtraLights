"""Generate grandMA2 fixture XMLs for the Theatrical Extra Lights RGB Bar.

Two modes, matching the block's DMX personalities:
- 4CH:  Dim, R, G, B for the whole bar (single instance).
- 28CH: Dim, then R/G/B per pixel, 9 pixels. Built as a multi-instance type (MAIN + 9 x PIXEL)
  so the desk shows the pixels as sub-fixtures (1.1 .. 1.9) and the effect engine, the colour
  picker and pixel layouts work per pixel.

Channel conventions come from gen_ma2_gobo.py in this folder.
"""
import sys
from xml.sax.saxutils import escape

from gen_ma2_gobo import dimmer, rgb

PIXELS = 9
NAME = "THEATRICAL RGB BAR"

HEADER = '''<?xml version="1.0" encoding="UTF-8"?>
<MA xmlns:xml="http://www.w3.org/XML/1998/namespace" major_vers="3" minor_vers="2" stream_vers="2">
  <Info datetime="2026-9-25T19:00:00" showfile="ma fixture builder"/>'''

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
    body = "\n".join(channels)
    return f'''      <Module index="{index}" name="{name}" class="LED" beamtype="Wash" beam_angle="28" beam_intensity="3000">
        <Body>
          <Size x="{size_x}" y="0.08" z="0.08"/>
        </Body>
{body}
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


def fixture_28():
    # Module 0 = master dimmer (channel 1), module 1 = one RGB pixel (3 channels, patched 9 times).
    main = module(0, "MAIN", "1.0", [dimmer(0, 1)])
    pixel = module(1, "PIXEL", str(round(1.0 / PIXELS, 3)), rgb_channels(0, 1))
    instances = ['      <Instance index="0" patch="1" module_index="0" name="Main"/>']
    for p in range(PIXELS):
        instances.append(f'      <Instance index="{p + 1}" patch="{2 + p * 3}" module_index="1" name="Pixel {p + 1}"/>')
    info = (f"Theatrical Extra Lights RGB Bar in the 28-channel pixel personality: channel 1 master dimmer, "
            f"then Red/Green/Blue for each of the {PIXELS} pixels, left to right. The pixels are sub-fixtures "
            f"1.1 to 1.{PIXELS}; the dimmer lives on the main fixture. Requires mod personality "
            f"\"28-Channel Pixel Mode (Dimmer + 9x RGB)\".")
    return f'''{HEADER}
  <FixtureType index="0" name="{NAME}" mode="28CH Pixel">
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
    with open(f"{out_dir}/nailec@theatrical_rgb_bar@28ch_pixel.xml", "w", encoding="utf-8") as f:
        f.write(fixture_28())
    print("ok")
