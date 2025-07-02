FROM alpine:3.19

ARG UID=1001
ARG GID=1001
ENV USER gerrit_admin
ENV USER_HOME /home/$USER
ENV WORKSPACE $USER_HOME/workspace

RUN apk --update add --no-cache openssh bash git util-linux openssl shadow curl jq
RUN echo "StrictHostKeyChecking no" >> /etc/ssh/ssh_config

RUN groupadd -f -g $GID users2
RUN useradd -u $UID -g $GID $USER
RUN mkdir -p $WORKSPACE $USER_HOME/.ssh
RUN chown -R $USER $USER_HOME

USER $USER

RUN ssh-keygen -P '' -f "$USER_HOME"/.ssh/id_rsa
RUN chmod 400 "$USER_HOME"/.ssh/id_rsa
RUN git config --global user.name "Gerrit Admin"
RUN git config --global user.email "gerrit_admin@example.com"

ENTRYPOINT ["tail", "-f", "/dev/null"]
