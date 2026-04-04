# capacitor-wallpaper-pro  •  CapacitorWallpaperPro.podspec

Pod::Spec.new do |s|
  s.name             = 'CapacitorWallpaperPro'
  s.version          = '1.0.0'
  s.summary          = 'Advanced wallpaper management for Capacitor apps'
  s.description      = <<~DESC
    Set wallpapers from remote URLs with filters (blur, brightness, contrast,
    saturation, sepia, vignette, hue, temperature), parallax, dual home/lock
    targets and a 24-hour schedule engine — all in one plugin call.
  DESC
  s.homepage         = 'https://github.com/your-org/capacitor-wallpaper-pro'
  s.license          = { type: 'MIT', file: '../LICENSE' }
  s.author           = { 'Your Name' => 'you@example.com' }
  s.source           = { git: 'https://github.com/your-org/capacitor-wallpaper-pro.git', tag: s.version.to_s }
  s.source_files     = 'Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
  s.ios.deployment_target = '13.0'
  s.dependency 'Capacitor'
  s.swift_version    = '5.5'
end
