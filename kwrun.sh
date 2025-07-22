kwcheck create
kwmaven -s /home/vttek/apache-maven-3.3.1/conf/settings.xml install --output ./chassis.out
kwcheck import ./chassis.out
kwcheck run
kwgcheck
