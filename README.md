# E-Ink Bike Tag for Caltrain

## What is this project?

A 3D-printed case for an e-ink display and an accompanying Android app that makes it quick and easy to update your destination when you ride Caltrain with a bike.

## Why is this project?

This project was inspired by my ridership patterns on Caltrain - fairly frequent, but rarely consistent. Normal bike tags never really worked for me - I live between two stations on a zone boundry, so I will go to whichever one saves me a few dollars. My destinations also varied as I mostly used Caltrain for non-commuting purposes. When I saw [this reddit post](https://www.reddit.com/r/caltrain/comments/1mhx5lh/i_made_a_programmable_bike_tag/) by u/BruceHalperin I was inspired to get the e-ink display. However, I wanted to build a case that would allow the bike tag to stay securely attached to my bike while keeping it protected; this seemed like a great opportunity to get into 3D printing. I was also not a fan of the app Waveshare provides to write content to the display as it was clunky and not well-suited for quickly writing a station a name to the display, as you had to type in the text each time. When I saw that Waveshare provides an Android SDK, I saw an opportunity to learn a little bit about Android development.

## Project Components

### The Display

The display is a Waveshare 2.7" parasitic-NFC-powered e-ink display. It uses power from your phone via NFC and updates the content on the screen. The resolution of 264 x 176 pixels is enough for a few lines of text.

### The Case

I designed a 3D-printed case in Autodesk Fusion based on measurements of the e-ink display PCB and measurements on Waveshare's website. It consists of a top lid and a bottom base which is screwed together with a M3x20mm screw with a nut and washer holding the PCB against the top lid and press-fit nuts (I used a little bit of heat to make insertion easier, but didn't have any heat-set inserts on hand) in the base holding everything together. The base has two slots in the to allow leave room for a velcro strap.

### The App

My idea for the app was to have a very simple list of stations that I can easily scroll through and select a station, then tap the display to update the station. I decided to pre-generate the images to simplify the app. This does reduce the flexibility of the app, but the point was to make it simple and quick to use. The Waveshare app could still be used to add custom text or images when needed, or you could create your own BMPs and recompile the app.

## Notes

### AI Disclosure

I used AI to help me with most aspects of this project, especially the app development. However, I was involved in every step and reviewed (as best I could) all the code.
