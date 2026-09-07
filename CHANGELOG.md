# 1.20.2-alpha.23

## Laser projector (Ether Dream)

- Rendu refait : la haze est maintenant produite par le moteur volumétrique raymarch
  (`beam_raymarch.fsh`) comme pour les autres projecteurs. Rayons aiguille avec halo
  Mie + nappes de balayage (« sheets ») sur les grands sweeps, cœurs blancs très fins
  par-dessus pour garder le dessin ILDA net, impacts sur les surfaces.
- Fin du clignotement : la fenêtre de reconstruction du DAC passe de 33 ms à
  `laserDacPersistenceMs` (90 ms par défaut) pour toujours couvrir une image ILDA
  complète quel que soit le fps de CloudLase ; chaque segment porte un âge et décroît
  comme la persistance rétinienne au lieu d'apparaître / disparaître.
- Publication de frame limitée à 250 Hz (au lieu d'un rebuild à chaque tick de
  lecture) et cache de raycast par direction (quantifié à 0,25°, invalidé au moindre
  pan/tilt).
- Nouvelles options de config : `laserDacPersistenceMs`, `laserDacVolumetricRays`,
  `laserDacVolumetricSheets`, `laserDacHazeRadius`, `laserDacSheetThickness`.
  `laserDacMaxRays` passe à 96 par défaut.
