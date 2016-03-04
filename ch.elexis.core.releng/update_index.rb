#!/usr/bin/env ruby
# encoding: utf-8
# Belongs to the project https://github.com/elexis/elexis-3-core/tree/master/ch.elexis.core.releng
# A copy is saved under /usr/local/bin on the srv.elexis.info
# Updates the index.html file of the Artikelstamm

require 'fileutils'
require 'pathname'

if Dir.pwd.eql?('/home/www/artikelstamm.elexis.info') ||
    File.directory?('/home/www/artikelstamm.elexis.info')
  OUTPUTFILE=File.join('/home/www/artikelstamm.elexis.info', 'index.html')
  Dir.chdir(File.dirname(OUTPUTFILE))
else
  OUTPUTFILE=File.join(Dir.pwd, 'index.html')
end

root_dir = Dir.pwd
a_files = {}
p_files = {}
n_files = {}

Versions =  { # 'v1' => 'Für Versuche mit Elexis 2.1.7',
              'v2' => 'Elexis 3.0',
              'v3' => 'Elexis 3.1',
#              'v4' => 'Elexis 3.2',
              }
Versions.keys.each do |version|
  a_files[version] = Dir.glob(root_dir+"/#{version}/*/artikelstamm_??????.xml")
  p_files[version] = Dir.glob(root_dir+"/#{version}/*/artikelstamm_P_*.xml")
  n_files[version] = Dir.glob(root_dir+"/#{version}/*/artikelstamm_P_*.xml")
  p_files[version].sort!{ |x,y| File.basename(y) <=> File.basename(x) }
  n_files[version].sort!{ |x,y| File.basename(y) <=> File.basename(x) }
  a_files[version].sort!{ |x,y| File.basename(y) <=> File.basename(x) }
end


header= %(
<HTML>
<HEAD>
   <meta charset='utf-8'>
 </HEAD>
<H1>artikelstamm.elexis.info on srv.elexis.info</H1>
<A HREF="documentation.html">Dokumentation</A>
<BR>           
<A HREF="datenbasis.html">Versionsvergleich und Datenbasis</A>
<BR>   
<A HREF="https://github.com/col-panic/elexis">Artikelstamm Source Code</A>
<BR>
<BR>
Für weitere Informationen: <A HREF="http://wiki.elexis.info/Installation_Elexis_3.0_OpenSource#Installation_Artikelstamm_und_Artikelstammdaten">http://wiki.elexis.info</A> oder <A HREF="http://www.medelexis.ch">www.medelexis.ch</A>
<P>
Besten Dank an unsere Sponsoren,
<ul>
<li>Dr. med Franz Marty (Sponsoring Artikelstamm Entwicklung)</li>
<li><A HREF="https://www.medelexis.ch/">Medelexis.</A> Entwicklung/Betreuung Elexis</li>
<li><A HREF="http://www.ywesee.com/">Zeno Davatz, ywesee GmbH.</A>Stellt die Daten
    <A HREF="http://pillbox.oddb.org/Datenfluss_Spital_Apotheke_Drogerie_1.png.html"/>zusammen</A> </li>
<li><A HREF="https://www.hin.ch/">HIN, Hosting oddb2xml-Daten</A></li>
<li><A HREF="http://www.zurrose.ch/">Apotheke Zur Rose (Daten zu nicht Pharma-Artikel)</A></li>
</ul>
welche uns ermöglichen, jeden Monat die aktuellsten Medikamentedaten zu beziehen!
<p>
Elexis 3.1 OpenSource Anwender müssen jeweils die richtige Version 3 des Artikelsstamm aus den unten angebenen Link herunterladen und importieren.
</p>
<ul>
)
footer = %(
</ul>
<p>
Die Artikelstamm Versionen 2 (für frühe Elexis 3.0 Versionen) und 1 (für Test-Versionen basieren auf Elexis 2.1.7) werden nicht mehr mit aktuellen Daten beliefert.
</p>
</HTML>
)
ausgabe=File.open(OUTPUTFILE, 'w+')
ausgabe.puts header
# require 'pry'; binding.pry

def emit_line_item(ausgabe, version, file, explanation)
  ausgabe.puts '<li>'
  relative =  Pathname.new(file).relative_path_from Pathname.new(Dir.pwd)
  latest = "latest_n_#{version}.xml"
  FileUtils.rm_f(latest, :verbose => true) if File.exist?(latest)
  FileUtils.ln_s relative, latest, :verbose => true
  ausgabe.puts "<a href=\"#{relative}\"/> latest_n_#{version}.xml</a> #{explanation}"
  ausgabe.puts '</li>'
end

Versions.sort.reverse.each do |version, explanation|

  ausgabe.puts "<li><b><a href=\"#{version}\"> #{version}</a>: Zu verwenden mit #{explanation}</b><ul>"
  if p_files[version]
    newest =  p_files[version].first
    emit_line_item(ausgabe, version, newest, 'Nur Pharma Artikel') if newest
  end
  if n_files[version]
    newest =  n_files[version].first
    emit_line_item(ausgabe, version, newest, 'Pharma und Non-Pharma Artikel') if newest
  end
  if a_files[version]
    newest =  a_files[version].first
    emit_line_item(ausgabe, version, newest, 'Vereinheitlichter Artikelstamm') if newest
  end
  ausgabe.puts '</ul></li>'
end

ausgabe.puts footer