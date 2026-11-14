[out:json][timeout:180];
area["ISO3166-1"="EG"][admin_level=2]->.egypt;
(
  nwr["highway"="speed_camera"](area.egypt);
  nwr["traffic_calming"](area.egypt);
);
out center tags;
